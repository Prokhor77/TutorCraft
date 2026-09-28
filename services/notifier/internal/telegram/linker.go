package telegram

import (
	"context"
	"log/slog"
	"regexp"
	"strings"
	"time"

	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/retry"
)

// Linking bot (hybrid mode, ADR-002): the web app shows a deep link
// https://t.me/<bot>?start=<code>; Telegram then sends "/start <code>".
const (
	TypeLinked        = "telegram.linked"
	startCommand      = "/start"
	privateChatType   = "private"
	minPollBackoff    = time.Second
	maxPollBackoff    = 30 * time.Second
	pollRequestMargin = 10 * time.Second
	ReplyLinked       = "Готово! Уведомления TutorCraft будут приходить сюда."
	ReplyInstructions = "Чтобы получать уведомления TutorCraft здесь, откройте на сайте «Настройки уведомлений» и нажмите «Подключить Telegram»."
	ReplyLinkFailed   = "Не удалось подключить уведомления. Попробуйте ещё раз через минуту."
)

// linkCodePattern matches core-api link codes (base64url, no padding).
var linkCodePattern = regexp.MustCompile(`^[A-Za-z0-9_-]{16,64}$`)

// UpdatesAPI is the part of the Bot API used by the linker.
type UpdatesAPI interface {
	MessageAPI
	GetUpdates(ctx context.Context, offset int64, timeoutSeconds int) ([]Update, error)
}

// EventPublisher publishes envelopes to Kafka.
type EventPublisher interface {
	PublishEvent(ctx context.Context, topic string, env envelope.Envelope) error
}

// Linked is the payload of tc.telegram.linked.v1.
type Linked struct {
	LinkCode       string `json:"linkCode"`
	ChatID         int64  `json:"chatId"`
	TelegramUserID int64  `json:"telegramUserId"`
	Username       string `json:"username,omitempty"`
}

// Linker long-polls getUpdates and turns "/start <code>" into linked events.
type Linker struct {
	API            UpdatesAPI
	Publisher      EventPublisher
	LinkedTopic    string
	PollTimeoutSec int
	Retry          retry.Policy
	Log            *slog.Logger
	Now            func() time.Time
}

// Run polls until ctx is cancelled. Offsets are tracked in memory: after a
// restart Telegram redelivers unconfirmed updates (at-least-once).
func (l *Linker) Run(ctx context.Context) error {
	var offset int64
	backoff := minPollBackoff
	for ctx.Err() == nil {
		updates, err := l.poll(ctx, offset)
		if err != nil {
			if ctx.Err() == nil {
				l.Log.Warn("telegram getUpdates failed", slog.String("error", err.Error()))
				sleep(ctx, backoff)
				backoff = min(backoff*2, maxPollBackoff)
			}
			continue
		}
		backoff = minPollBackoff
		for _, update := range updates {
			l.handle(ctx, update)
			offset = update.UpdateID + 1
		}
	}
	return nil
}

func (l *Linker) poll(ctx context.Context, offset int64) ([]Update, error) {
	pollCtx, cancel := context.WithTimeout(ctx, time.Duration(l.PollTimeoutSec)*time.Second+pollRequestMargin)
	defer cancel()
	return l.API.GetUpdates(pollCtx, offset, l.PollTimeoutSec)
}

func (l *Linker) handle(ctx context.Context, update Update) {
	msg := update.Message
	if msg == nil || msg.From == nil || msg.Chat.Type != privateChatType {
		return
	}
	isStart, code := parseStart(msg.Text)
	if !isStart {
		return
	}
	log := l.Log.With(slog.Int64("updateId", update.UpdateID))
	if !linkCodePattern.MatchString(code) {
		l.reply(ctx, msg.Chat.ID, ReplyInstructions, log)
		return
	}
	linked := Linked{LinkCode: code, ChatID: msg.Chat.ID, TelegramUserID: msg.From.ID, Username: msg.From.Username}
	if err := retry.Do(ctx, l.Retry, func(ctx context.Context) error { return l.publish(ctx, linked) }); err != nil {
		log.Error("telegram link event not published", slog.String("error", err.Error()))
		l.reply(ctx, msg.Chat.ID, ReplyLinkFailed, log)
		return
	}
	log.Info("telegram link event published")
	l.reply(ctx, msg.Chat.ID, ReplyLinked, log)
}

// parseStart recognises "/start" and "/start <code>" (also "/start@BotName").
func parseStart(text string) (bool, string) {
	fields := strings.Fields(text)
	if len(fields) == 0 {
		return false, ""
	}
	command, _, _ := strings.Cut(fields[0], "@")
	if command != startCommand {
		return false, ""
	}
	if len(fields) < 2 {
		return true, ""
	}
	return true, fields[1]
}

// publish uses the nil tenant: the tenant is resolved by core-api from the link code.
func (l *Linker) publish(ctx context.Context, linked Linked) error {
	env, err := envelope.New(TypeLinked, envelope.NilTenantID, linked, l.Now())
	if err != nil {
		return retry.Permanent(err)
	}
	return l.Publisher.PublishEvent(ctx, l.LinkedTopic, env)
}

func (l *Linker) reply(ctx context.Context, chatID int64, text string, log *slog.Logger) {
	if err := l.API.SendMessage(ctx, SendMessageRequest{ChatID: chatID, Text: text}); err != nil {
		log.Warn("telegram reply failed", slog.String("error", err.Error()))
	}
}

func sleep(ctx context.Context, d time.Duration) {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
	case <-timer.C:
	}
}
