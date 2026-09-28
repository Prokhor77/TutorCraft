package video

import (
	"fmt"
	"path"
	"path/filepath"
	"strconv"
	"strings"
)

// HLS output conventions (ADR-008): hls/{fileId}/master.m3u8 and
// hls/{fileId}/{rendition}/index.m3u8 + seg_NNNNN.ts.
const (
	SegmentSeconds        = 6
	MasterPlaylistName    = "master.m3u8"
	VariantPlaylistName   = "index.m3u8"
	segmentFilePattern    = "seg_%05d.ts"
	variantDirPlaceholder = "%v"
	hlsRootPrefix         = "hls"
	maxRateFactor         = 1.07
	bufferSizeFactor      = 1.5
	audioChannels         = "2"
	videoCodec            = "libx264"
	audioCodec            = "aac"
	x264Preset            = "veryfast"
	h264Profile           = "main"
	pixelFormat           = "yuv420p"
	inputProtocols        = "file"
	fileProtocolPrefix    = "file:"
)

// Content types for uploaded HLS files.
const (
	ContentTypePlaylist = "application/vnd.apple.mpegurl"
	ContentTypeSegment  = "video/mp2t"
	ContentTypeDefault  = "application/octet-stream"
)

// ContentTypeFor returns the Content-Type for an HLS output file name.
func ContentTypeFor(name string) string {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".m3u8":
		return ContentTypePlaylist
	case ".ts":
		return ContentTypeSegment
	default:
		return ContentTypeDefault
	}
}

// Layout maps a file's HLS output to S3 keys.
type Layout struct {
	FileID string
}

// Prefix is the key prefix of all HLS objects of the file ("hls/{fileId}/").
func (l Layout) Prefix() string {
	return hlsRootPrefix + "/" + l.FileID + "/"
}

// MasterPlaylistKey is the key of the master playlist.
func (l Layout) MasterPlaylistKey() string {
	return l.Prefix() + MasterPlaylistName
}

// KeyFor maps a path relative to the output directory to an S3 key.
func (l Layout) KeyFor(relativePath string) string {
	return l.Prefix() + path.Clean(filepath.ToSlash(relativePath))
}

// HLSJob describes one transcoding run. Paths are local and worker-controlled.
type HLSJob struct {
	InputPath  string
	OutputDir  string
	Renditions []Rendition
	HasAudio   bool
}

// FFmpegArgs builds the ffmpeg argument list (no shell involved) that
// transcodes the input into all renditions plus a master playlist in one pass.
func FFmpegArgs(job HLSJob) []string {
	args := []string{
		"-hide_banner", "-nostdin", "-loglevel", "error", "-y",
		"-protocol_whitelist", inputProtocols, "-i", fileProtocolPrefix + job.InputPath,
		"-map_metadata", "-1", "-map_chapters", "-1",
		"-filter_complex", scaleFilter(job.Renditions),
	}
	for i, r := range job.Renditions {
		args = append(args, videoStreamArgs(i, r)...)
	}
	if job.HasAudio {
		for i, r := range job.Renditions {
			args = append(args, audioStreamArgs(i, r)...)
		}
		args = append(args, "-ac", audioChannels)
	}
	args = append(args, encoderArgs()...)
	return append(args, hlsMuxerArgs(job)...)
}

// scaleFilter splits the input video once per rendition and scales each copy.
func scaleFilter(renditions []Rendition) string {
	splitOutputs := make([]string, len(renditions))
	scales := make([]string, len(renditions))
	for i, r := range renditions {
		splitOutputs[i] = fmt.Sprintf("[s%d]", i)
		scales[i] = fmt.Sprintf("[s%d]scale=-2:%d[v%d]", i, r.Height, i)
	}
	split := fmt.Sprintf("[0:v]split=%d%s", len(renditions), strings.Join(splitOutputs, ""))
	return split + ";" + strings.Join(scales, ";")
}

func videoStreamArgs(i int, r Rendition) []string {
	n := strconv.Itoa(i)
	return []string{
		"-map", fmt.Sprintf("[v%d]", i),
		"-c:v:" + n, videoCodec,
		"-b:v:" + n, kbps(r.VideoBitrate),
		"-maxrate:v:" + n, kbps(int64(float64(r.VideoBitrate) * maxRateFactor)),
		"-bufsize:v:" + n, kbps(int64(float64(r.VideoBitrate) * bufferSizeFactor)),
	}
}

func audioStreamArgs(i int, r Rendition) []string {
	n := strconv.Itoa(i)
	return []string{"-map", "0:a:0", "-c:a:" + n, audioCodec, "-b:a:" + n, kbps(r.AudioBitrate)}
}

// encoderArgs force a keyframe at every segment boundary so that segments
// of all renditions align (required for adaptive switching).
func encoderArgs() []string {
	return []string{
		"-preset", x264Preset, "-profile:v", h264Profile, "-pix_fmt", pixelFormat, "-sc_threshold", "0",
		"-force_key_frames", fmt.Sprintf("expr:gte(t,n_forced*%d)", SegmentSeconds),
	}
}

func hlsMuxerArgs(job HLSJob) []string {
	variantDir := filepath.Join(job.OutputDir, variantDirPlaceholder)
	return []string{
		"-f", "hls",
		"-hls_time", strconv.Itoa(SegmentSeconds),
		"-hls_playlist_type", "vod",
		"-hls_flags", "independent_segments",
		"-hls_segment_type", "mpegts",
		"-hls_segment_filename", filepath.Join(variantDir, segmentFilePattern),
		"-master_pl_name", MasterPlaylistName,
		"-var_stream_map", varStreamMap(job),
		filepath.Join(variantDir, VariantPlaylistName),
	}
}

// varStreamMap names each variant after its rendition, e.g. "v:0,a:0,name:360p".
func varStreamMap(job HLSJob) string {
	entries := make([]string, len(job.Renditions))
	for i, r := range job.Renditions {
		entry := fmt.Sprintf("v:%d", i)
		if job.HasAudio {
			entry += fmt.Sprintf(",a:%d", i)
		}
		entries[i] = entry + ",name:" + r.Name
	}
	return strings.Join(entries, " ")
}

// FFprobeArgs builds the ffprobe argument list for a local file.
func FFprobeArgs(inputPath string) []string {
	return []string{
		"-v", "error", "-protocol_whitelist", inputProtocols,
		"-print_format", "json", "-show_format", "-show_streams",
		fileProtocolPrefix + inputPath,
	}
}
