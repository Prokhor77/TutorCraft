package telegram

// Bot API types (subset). https://core.telegram.org/bots/api

// SendMessageRequest is the sendMessage body.
type SendMessageRequest struct {
	ChatID             int64                 `json:"chat_id"`
	Text               string                `json:"text"`
	ParseMode          string                `json:"parse_mode,omitempty"`
	LinkPreviewOptions *LinkPreviewOptions   `json:"link_preview_options,omitempty"`
	ReplyMarkup        *InlineKeyboardMarkup `json:"reply_markup,omitempty"`
}

// LinkPreviewOptions controls link previews.
type LinkPreviewOptions struct {
	IsDisabled bool `json:"is_disabled"`
}

// InlineKeyboardMarkup is a keyboard attached to a message.
type InlineKeyboardMarkup struct {
	InlineKeyboard [][]InlineKeyboardButton `json:"inline_keyboard"`
}

// InlineKeyboardButton opens URL when pressed.
type InlineKeyboardButton struct {
	Text string `json:"text"`
	URL  string `json:"url"`
}

type getUpdatesRequest struct {
	Offset         int64    `json:"offset"`
	Timeout        int      `json:"timeout"`
	AllowedUpdates []string `json:"allowed_updates"`
}

// Update is an incoming update.
type Update struct {
	UpdateID int64    `json:"update_id"`
	Message  *Message `json:"message"`
}

// Message is an incoming message.
type Message struct {
	MessageID int64  `json:"message_id"`
	From      *User  `json:"from"`
	Chat      Chat   `json:"chat"`
	Text      string `json:"text"`
}

// User is a Telegram user.
type User struct {
	ID       int64  `json:"id"`
	Username string `json:"username"`
}

// Chat is a Telegram chat.
type Chat struct {
	ID   int64  `json:"id"`
	Type string `json:"type"`
}
