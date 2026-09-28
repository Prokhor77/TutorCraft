package s3

import (
	"encoding/hex"
	"net/http"
	"testing"
	"time"
)

// Official example "GET Object" from
// https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
const (
	awsExampleAccessKey = "AKIAIOSFODNN7EXAMPLE"
	awsExampleSecretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
	awsExampleSignature = "f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"
	awsExampleCanonical = "GET\n/test.txt\n\nhost:examplebucket.s3.amazonaws.com\nrange:bytes=0-9\n" +
		"x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n" +
		"x-amz-date:20130524T000000Z\n\nhost;range;x-amz-content-sha256;x-amz-date\n" +
		"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
)

func awsExampleRequest(t *testing.T) (*http.Request, time.Time) {
	t.Helper()
	req, err := http.NewRequest(http.MethodGet, "https://examplebucket.s3.amazonaws.com/test.txt", nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Range", "bytes=0-9")
	return req, time.Date(2013, 5, 24, 0, 0, 0, 0, time.UTC)
}

func TestSignMatchesAWSGetObjectExample(t *testing.T) {
	req, now := awsExampleRequest(t)
	signer := Signer{Credentials: Credentials{awsExampleAccessKey, awsExampleSecretKey}, Region: "us-east-1"}
	signer.Sign(req, EmptyPayloadHash, now)
	want := "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request," +
		" SignedHeaders=host;range;x-amz-content-sha256;x-amz-date," +
		" Signature=" + awsExampleSignature
	if got := req.Header.Get(HeaderAuthorization); got != want {
		t.Fatalf("authorization mismatch\n got: %s\nwant: %s", got, want)
	}
}

func TestCanonicalRequestMatchesAWSExample(t *testing.T) {
	req, now := awsExampleRequest(t)
	req.Header.Set(HeaderAmzDate, now.Format(amzDateLayout))
	req.Header.Set(HeaderContentSHA256, EmptyPayloadHash)
	signed, block := canonicalHeaders(req)
	if got := canonicalRequest(req, block, signed, EmptyPayloadHash); got != awsExampleCanonical {
		t.Fatalf("canonical request mismatch\n got: %q\nwant: %q", got, awsExampleCanonical)
	}
}

// Signing-key example from https://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html
func TestDeriveSigningKeyMatchesAWSExample(t *testing.T) {
	key := deriveSigningKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20120215", "us-east-1", "iam")
	const want = "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d"
	if got := hex.EncodeToString(key); got != want {
		t.Fatalf("signing key = %s, want %s", got, want)
	}
}

func TestEncodePath(t *testing.T) {
	cases := map[string]string{
		"":                       "/",
		"/bucket/hls/a b.ts":     "/bucket/hls/a%20b.ts",
		"/bucket/t/ключ~_-.m3u8": "/bucket/t/%D0%BA%D0%BB%D1%8E%D1%87~_-.m3u8",
		"/b/a+b=c":               "/b/a%2Bb%3Dc",
	}
	for in, want := range cases {
		if got := encodePath(in); got != want {
			t.Errorf("encodePath(%q) = %q, want %q", in, got, want)
		}
	}
}
