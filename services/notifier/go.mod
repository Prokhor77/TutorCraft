module github.com/tutorcraft/notifier

go 1.24

require (
	github.com/golang-jwt/jwt/v5 v5.3.1
	github.com/gorilla/websocket v1.5.3
	github.com/tutorcraft/workerkit v0.0.0
)

require (
	github.com/klauspost/compress v1.15.9 // indirect
	github.com/pierrec/lz4/v4 v4.1.15 // indirect
	github.com/segmentio/kafka-go v0.4.51 // indirect
)

// Shared worker plumbing lives next to the services (services/workerkit).
replace github.com/tutorcraft/workerkit => ../workerkit

// Only GitHub is reachable from the build network: golang.org/x and gopkg.in
// modules (pulled in by kafka-go and its tests) are replaced with their
// GitHub mirrors, which declare the original module paths. They are needed
// for `go mod tidy` only; the service binaries do not link them.
replace (
	golang.org/x/net => github.com/golang/net v0.38.0
	golang.org/x/text => github.com/golang/text v0.23.0
	gopkg.in/yaml.v3 => github.com/go-yaml/yaml v0.0.0-20220527083530-f6f7691b1fde
)
