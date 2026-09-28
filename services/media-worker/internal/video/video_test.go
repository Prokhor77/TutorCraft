package video

import (
	"encoding/json"
	"errors"
	"slices"
	"strings"
	"testing"

	"github.com/tutorcraft/workerkit/validate"
)

const (
	testFileID  = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	testOwnerID = "0192f3c1-0000-7000-8000-000000000002"
)

func validPayload() map[string]any {
	return map[string]any{
		"fileId": testFileID, "bucket": "tutorcraft", "objectKey": "t/tenant/f/original.mp4",
		"contentType": "video/mp4", "sizeBytes": 1024, "ownerUserId": testOwnerID, "extra": "ignored",
	}
}

func decode(t *testing.T, payload map[string]any) (Uploaded, error) {
	t.Helper()
	raw, err := json.Marshal(payload)
	if err != nil {
		t.Fatal(err)
	}
	return DecodeUploaded(raw)
}

func TestDecodeUploadedValid(t *testing.T) {
	u, err := decode(t, validPayload())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if u.FileID != testFileID || u.SizeBytes != 1024 || u.ObjectKey != "t/tenant/f/original.mp4" {
		t.Fatalf("unexpected payload %+v", u)
	}
}

func TestDecodeUploadedRejectsInvalidFields(t *testing.T) {
	cases := map[string]func(p map[string]any){
		"fileId not uuid":  func(p map[string]any) { p["fileId"] = "42" },
		"missing owner":    func(p map[string]any) { delete(p, "ownerUserId") },
		"missing bucket":   func(p map[string]any) { p["bucket"] = "" },
		"traversal key":    func(p map[string]any) { p["objectKey"] = "t/../secret" },
		"absolute key":     func(p map[string]any) { p["objectKey"] = "/etc/passwd" },
		"control char key": func(p map[string]any) { p["objectKey"] = "a\nb" },
		"not a video":      func(p map[string]any) { p["contentType"] = "application/pdf" },
		"missing size":     func(p map[string]any) { delete(p, "sizeBytes") },
		"zero size":        func(p map[string]any) { p["sizeBytes"] = 0 },
		"size wrong type":  func(p map[string]any) { p["sizeBytes"] = "big" },
	}
	for name, mutate := range cases {
		p := validPayload()
		mutate(p)
		_, err := decode(t, p)
		var verr *validate.Error
		if !errors.As(err, &verr) {
			t.Errorf("%s: want validation error, got %v", name, err)
		}
	}
}

func TestSelectRenditions(t *testing.T) {
	cases := map[int][]string{
		240:  {"360p"},
		480:  {"360p"},
		719:  {"360p"},
		720:  {"360p", "720p"},
		1080: {"360p", "720p"},
	}
	for height, want := range cases {
		var got []string
		for _, r := range SelectRenditions(height) {
			got = append(got, r.Name)
		}
		if !slices.Equal(got, want) {
			t.Errorf("height %d: got %v, want %v", height, got, want)
		}
	}
}

func TestParseProbe(t *testing.T) {
	data := []byte(`{"streams":[
		{"codec_type":"video","width":1920,"height":1080},
		{"codec_type":"audio"}],
		"format":{"duration":"12.480000"}}`)
	info, err := ParseProbe(data)
	if err != nil {
		t.Fatal(err)
	}
	if info.Height != 1080 || info.Width != 1920 || !info.HasAudio || info.DurationSec != 12.48 {
		t.Fatalf("unexpected info %+v", info)
	}
}

func TestParseProbeAppliesRotation(t *testing.T) {
	data := []byte(`{"streams":[{"codec_type":"video","width":1920,"height":1080,
		"side_data_list":[{"rotation":-90}]}],"format":{"duration":"3"}}`)
	info, err := ParseProbe(data)
	if err != nil {
		t.Fatal(err)
	}
	if info.Height != 1920 || info.Width != 1080 || info.HasAudio {
		t.Fatalf("rotation not applied: %+v", info)
	}
}

func TestParseProbeErrors(t *testing.T) {
	cases := map[string]error{
		`{"streams":[{"codec_type":"audio"}],"format":{"duration":"3"}}`:                          ErrNoVideoStream,
		`{"streams":[{"codec_type":"video","width":10,"height":10}],"format":{"duration":"N/A"}}`: ErrInvalidDuration,
	}
	for input, want := range cases {
		if _, err := ParseProbe([]byte(input)); !errors.Is(err, want) {
			t.Errorf("ParseProbe(%s) = %v, want %v", input, err, want)
		}
	}
	if _, err := ParseProbe([]byte(`not json`)); err == nil {
		t.Error("expected JSON error")
	}
}

func TestFFmpegArgsTwoRenditionsWithAudio(t *testing.T) {
	args := FFmpegArgs(HLSJob{InputPath: "/w/source", OutputDir: "/w/out", Renditions: Ladder, HasAudio: true})
	joined := strings.Join(args, " ")
	mustContain := []string{
		"-protocol_whitelist file -i file:/w/source",
		"-filter_complex [0:v]split=2[s0][s1];[s0]scale=-2:360[v0];[s1]scale=-2:720[v1]",
		"-map [v0] -c:v:0 libx264 -b:v:0 800k -maxrate:v:0 856k -bufsize:v:0 1200k",
		"-map [v1] -c:v:1 libx264 -b:v:1 2800k",
		"-map 0:a:0 -c:a:0 aac -b:a:0 96k",
		"-map 0:a:0 -c:a:1 aac -b:a:1 128k -ac 2",
	}
	for _, s := range mustContain {
		if !strings.Contains(joined, s) {
			t.Errorf("missing %q in %s", s, joined)
		}
	}
	assertArg(t, args, "-hls_time", "6")
	assertArg(t, args, "-hls_playlist_type", "vod")
	assertArg(t, args, "-master_pl_name", "master.m3u8")
	assertArg(t, args, "-var_stream_map", "v:0,a:0,name:360p v:1,a:1,name:720p")
	assertArg(t, args, "-hls_segment_filename", "/w/out/%v/seg_%05d.ts")
	if args[len(args)-1] != "/w/out/%v/index.m3u8" {
		t.Errorf("output = %s", args[len(args)-1])
	}
}

func TestFFmpegArgsWithoutAudio(t *testing.T) {
	args := FFmpegArgs(HLSJob{InputPath: "/w/source", OutputDir: "/w/out", Renditions: Ladder[:1]})
	joined := strings.Join(args, " ")
	if strings.Contains(joined, "0:a:0") || strings.Contains(joined, "-ac") {
		t.Errorf("audio args present without audio: %s", joined)
	}
	assertArg(t, args, "-var_stream_map", "v:0,name:360p")
	assertArg(t, args, "-filter_complex", "[0:v]split=1[s0];[s0]scale=-2:360[v0]")
}

func assertArg(t *testing.T, args []string, flag, want string) {
	t.Helper()
	i := slices.Index(args, flag)
	if i < 0 || i+1 >= len(args) || args[i+1] != want {
		t.Errorf("%s: want %q in %v", flag, want, args)
	}
}

func TestLayoutAndContentTypes(t *testing.T) {
	l := Layout{FileID: testFileID}
	if l.MasterPlaylistKey() != "hls/"+testFileID+"/master.m3u8" {
		t.Errorf("master key %s", l.MasterPlaylistKey())
	}
	if l.KeyFor("360p/seg_00001.ts") != "hls/"+testFileID+"/360p/seg_00001.ts" {
		t.Errorf("segment key %s", l.KeyFor("360p/seg_00001.ts"))
	}
	if ContentTypeFor("index.m3u8") != ContentTypePlaylist || ContentTypeFor("a.TS") != ContentTypeSegment ||
		ContentTypeFor("x.bin") != ContentTypeDefault {
		t.Error("content types")
	}
}

func TestFailureMessageIsSanitized(t *testing.T) {
	e := &UnprocessableError{Reason: ReasonTranscodeFailed, Detail: "/tmp/job-1/source: Invalid data\nfound " +
		strings.Repeat("x", 600)}
	msg := FailureMessage(e, "/tmp/job-1")
	if strings.Contains(msg, "/tmp/job-1") || strings.Contains(msg, "\n") {
		t.Errorf("not sanitized: %q", msg)
	}
	if n := len([]rune(msg)); n > MaxErrorRunes {
		t.Errorf("length %d", n)
	}
	if !strings.HasPrefix(msg, "transcoding failed: /source: Invalid data found") {
		t.Errorf("unexpected message %q", msg)
	}
}

func TestProcessedJSONShape(t *testing.T) {
	ready, _ := json.Marshal(Ready(testFileID, Layout{FileID: testFileID}, 13,
		[]RenditionInfo{Ladder[0].Info(true)}))
	want := `{"fileId":"` + testFileID + `","status":"ready","hlsPrefix":"hls/` + testFileID +
		`/","masterPlaylistKey":"hls/` + testFileID + `/master.m3u8","durationSec":13,` +
		`"renditions":[{"name":"360p","height":360,"bandwidth":896000}]}`
	if string(ready) != want {
		t.Errorf("ready JSON\n got %s\nwant %s", ready, want)
	}
	failed, _ := json.Marshal(Failed(testFileID, "boom"))
	if string(failed) != `{"fileId":"`+testFileID+`","status":"failed","error":"boom"}` {
		t.Errorf("failed JSON %s", failed)
	}
}
