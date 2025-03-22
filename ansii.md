# Cursor Movement:
\033[A	Move cursor up one line
\033[B	Move cursor down one line
\033[C	Move cursor right one character
\033[D	Move cursor left one character
\033[nC	Move cursor n characters right
\033[nD	Move cursor n characters left
\033[nA	Move cursor n lines up
\033[nB	Move cursor n lines down
\033[nG	Move cursor to column n
\033[n; mH	Move cursor to row n, column m

# Clearing Text:
\033[2J	Clear entire screen
\033[K	Clear from cursor to end of line
\033[1K	Clear from cursor to beginning of line
\033[2K	Clear entire line

# Saving & Restoring Cursor Position:
\033[s	Save current cursor position
\033[u	Restore last saved cursor position