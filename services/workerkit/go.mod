module github.com/tutorcraft/workerkit

go 1.24

require github.com/segmentio/kafka-go v0.4.51

require (
	github.com/klauspost/compress v1.15.9 // indirect
	github.com/pierrec/lz4/v4 v4.1.15 // indirect
)

// Only GitHub is reachable from the build network: golang.org/x and gopkg.in
// modules (pulled in by kafka-go and its tests) are replaced with their
// GitHub mirrors, which declare the original module paths. They are needed
// for `go mod tidy` only; the service binaries do not link them.
replace (
	golang.org/x/net => github.com/golang/net v0.38.0
	golang.org/x/text => github.com/golang/text v0.23.0
	gopkg.in/yaml.v3 => github.com/go-yaml/yaml v0.0.0-20220527083530-f6f7691b1fde
)
