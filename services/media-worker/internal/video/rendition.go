package video

import "fmt"

const bitsPerKilobit = 1000

// Rendition is one rung of the HLS bitrate ladder.
type Rendition struct {
	Name         string
	Height       int
	VideoBitrate int64 // bits per second
	AudioBitrate int64 // bits per second
}

// Ladder is the full rendition ladder, lowest first (ARCH §5.2: 360p/720p).
var Ladder = []Rendition{
	{Name: "360p", Height: 360, VideoBitrate: 800_000, AudioBitrate: 96_000},
	{Name: "720p", Height: 720, VideoBitrate: 2_800_000, AudioBitrate: 128_000},
}

// SelectRenditions picks the renditions for a source of the given height:
// the lowest rung always, higher rungs only when they do not upscale.
func SelectRenditions(sourceHeight int) []Rendition {
	selected := []Rendition{Ladder[0]}
	for _, r := range Ladder[1:] {
		if sourceHeight >= r.Height {
			selected = append(selected, r)
		}
	}
	return selected
}

// Bandwidth is the nominal peak bitrate reported in the processed event.
func (r Rendition) Bandwidth(hasAudio bool) int64 {
	if hasAudio {
		return r.VideoBitrate + r.AudioBitrate
	}
	return r.VideoBitrate
}

// Info converts the rendition to its event representation.
func (r Rendition) Info(hasAudio bool) RenditionInfo {
	return RenditionInfo{Name: r.Name, Height: r.Height, Bandwidth: r.Bandwidth(hasAudio)}
}

func kbps(bitsPerSecond int64) string {
	return fmt.Sprintf("%dk", bitsPerSecond/bitsPerKilobit)
}
