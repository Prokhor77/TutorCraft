// Package video holds the media-worker domain: event payloads and their
// validation, the rendition ladder, probe parsing and the HLS job description.
// It has no I/O.
package video

import (
	"encoding/json"
	"strings"

	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/validate"
)

// Event types (envelope "type") and processing statuses (docs/events/README.md).
const (
	TypeVideoProcessed = "media.video-processed"
	StatusReady        = "ready"
	StatusFailed       = "failed"
	videoContentPrefix = "video/"
	maxObjectKeyRunes  = 1024
	maxContentTypeLen  = 255
	parentDirSegment   = ".."
)

// Uploaded is the payload of tc.media.video-uploaded.v1.
type Uploaded struct {
	FileID      string `json:"fileId"`
	Bucket      string `json:"bucket"`
	ObjectKey   string `json:"objectKey"`
	ContentType string `json:"contentType"`
	SizeBytes   int64  `json:"sizeBytes"`
	OwnerUserID string `json:"ownerUserId"`
}

// uploadedWire detects a missing sizeBytes.
type uploadedWire struct {
	Uploaded
	SizeBytes *int64 `json:"sizeBytes"`
}

// DecodeUploaded parses and validates the payload.
func DecodeUploaded(raw json.RawMessage) (Uploaded, error) {
	var w uploadedWire
	if err := envelope.DecodePayload(raw, &w); err != nil {
		return Uploaded{}, err
	}
	var c validate.Collector
	c.UUID("fileId", w.FileID)
	c.UUID("ownerUserId", w.OwnerUserID)
	c.Required("bucket", w.Bucket)
	c.Check(isSafeObjectKey(w.ObjectKey), "objectKey", validate.ProblemInvalid)
	c.MaxLen("objectKey", w.ObjectKey, maxObjectKeyRunes)
	c.Check(strings.HasPrefix(w.ContentType, videoContentPrefix), "contentType", "must be video/*")
	c.MaxLen("contentType", w.ContentType, maxContentTypeLen)
	c.Check(w.SizeBytes != nil && *w.SizeBytes > 0, "sizeBytes", "must be a positive integer")
	if err := c.Err(); err != nil {
		return Uploaded{}, err
	}
	u := w.Uploaded
	u.SizeBytes = *w.SizeBytes
	return u, nil
}

// isSafeObjectKey rejects empty keys, absolute keys, ".." segments and control characters.
func isSafeObjectKey(key string) bool {
	if key == "" || strings.HasPrefix(key, "/") {
		return false
	}
	for _, segment := range strings.Split(key, "/") {
		if segment == parentDirSegment {
			return false
		}
	}
	return !strings.ContainsFunc(key, func(r rune) bool { return r < ' ' || r == 0x7f })
}

// RenditionInfo describes one HLS variant in the processed event.
type RenditionInfo struct {
	Name      string `json:"name"`
	Height    int    `json:"height"`
	Bandwidth int64  `json:"bandwidth"`
}

// Processed is the payload of tc.media.video-processed.v1.
type Processed struct {
	FileID            string          `json:"fileId"`
	Status            string          `json:"status"`
	HLSPrefix         string          `json:"hlsPrefix,omitempty"`
	MasterPlaylistKey string          `json:"masterPlaylistKey,omitempty"`
	DurationSec       *int            `json:"durationSec,omitempty"`
	Renditions        []RenditionInfo `json:"renditions,omitempty"`
	Error             string          `json:"error,omitempty"`
}

// Ready builds a successful result.
func Ready(fileID string, layout Layout, durationSec int, renditions []RenditionInfo) Processed {
	return Processed{
		FileID:            fileID,
		Status:            StatusReady,
		HLSPrefix:         layout.Prefix(),
		MasterPlaylistKey: layout.MasterPlaylistKey(),
		DurationSec:       &durationSec,
		Renditions:        renditions,
	}
}

// Failed builds a failure result; reason must already be sanitized.
func Failed(fileID, reason string) Processed {
	return Processed{FileID: fileID, Status: StatusFailed, Error: reason}
}
