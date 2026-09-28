package video

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
)

const (
	codecTypeVideo  = "video"
	codecTypeAudio  = "audio"
	quarterTurn     = 90
	fullTurn        = 360
	rotateTagName   = "rotate"
	halfTurnDegrees = 180
)

var (
	ErrNoVideoStream   = errors.New("no video stream")
	ErrInvalidDuration = errors.New("unknown or zero duration")
)

// MediaInfo is what the worker needs to know about a source file.
type MediaInfo struct {
	DurationSec float64
	Width       int
	Height      int // display height (rotation applied)
	HasAudio    bool
}

type probeOutput struct {
	Streams []probeStream `json:"streams"`
	Format  struct {
		Duration string `json:"duration"`
	} `json:"format"`
}

type probeStream struct {
	CodecType    string            `json:"codec_type"`
	Width        int               `json:"width"`
	Height       int               `json:"height"`
	Duration     string            `json:"duration"`
	Tags         map[string]string `json:"tags"`
	SideDataList []struct {
		Rotation float64 `json:"rotation"`
	} `json:"side_data_list"`
}

// ParseProbe interprets `ffprobe -print_format json -show_format -show_streams`.
func ParseProbe(data []byte) (MediaInfo, error) {
	var out probeOutput
	if err := json.Unmarshal(data, &out); err != nil {
		return MediaInfo{}, fmt.Errorf("parse ffprobe output: %w", err)
	}
	videoStream, ok := findStream(out.Streams, codecTypeVideo)
	if !ok || videoStream.Width <= 0 || videoStream.Height <= 0 {
		return MediaInfo{}, ErrNoVideoStream
	}
	duration := parseSeconds(out.Format.Duration)
	if duration <= 0 {
		duration = parseSeconds(videoStream.Duration)
	}
	if duration <= 0 {
		return MediaInfo{}, ErrInvalidDuration
	}
	_, hasAudio := findStream(out.Streams, codecTypeAudio)
	width, height := displaySize(videoStream)
	return MediaInfo{DurationSec: duration, Width: width, Height: height, HasAudio: hasAudio}, nil
}

func findStream(streams []probeStream, codecType string) (probeStream, bool) {
	for _, s := range streams {
		if s.CodecType == codecType {
			return s, true
		}
	}
	return probeStream{}, false
}

func parseSeconds(s string) float64 {
	v, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return 0
	}
	return v
}

// displaySize swaps width/height for portrait phone videos stored rotated.
func displaySize(s probeStream) (int, int) {
	rotation := rotationDegrees(s)
	if rotation%halfTurnDegrees == quarterTurn {
		return s.Height, s.Width
	}
	return s.Width, s.Height
}

func rotationDegrees(s probeStream) int {
	degrees := 0
	if tag, ok := s.Tags[rotateTagName]; ok {
		if parsed, err := strconv.Atoi(tag); err == nil {
			degrees = parsed
		}
	}
	for _, side := range s.SideDataList {
		if side.Rotation != 0 {
			degrees = int(side.Rotation)
		}
	}
	return ((degrees % fullTurn) + fullTurn) % fullTurn
}
