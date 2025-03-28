package brandon.gpt;

import java.io.*;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    
    public static String API_KEY = "";  // Replace with your actual OpenAI API key

    public static int MAX_TOKENS = 4096;
    public static double TEMPERATURE = 0.5;
    public static String PROMPT_DIVIDER = "----------------------------------------";
    public static List<String> EXIT_COMMANDS = List.of("exit", "quit", "close");
    public static String FILE = "chat.md";
    public static String MODEL = "gpt-4o-mini";
    public static final String API_URL = "https://api.openai.com/v1/chat/completions";
    public static final int HISTORY_LENGTH = 20;


    private static final String starterMessage = PROMPT_DIVIDER + Role.user.name() + "\n\n\n";
    public static void main(String[] args) throws IOException {


        // parse options
        Map<String, String> shortArgs = Map.of(
            "-k", 
            """
            Sets the open-api key to be used in requests.
            If you wish to not specify this everytime, we
            reccomend either making an alias or inserting 
            your api key in the Main.java file in the 
            repository and building the jar.
            """,
            "-m",
            """
            The model name param sent to the api. The 
            default is 'gpt-4o-mini'. 
            """,
            "-t",
            """
            Sets the max tokens param sent to the api. 
            Defaults to 4096.
            """,
            "-T",
            """
            Sets the temperature param sent to the api.
            For tasks where you want more accurate answers
            This should be a value < 0.5. For more creative
            answers this can be more, even 1.0+.
            """
        );

        Map<String, String> longFlags = Map.of(
            "--api-key", 
            """
            --api-key=sk-proj-...
            Sets the open-api key to be used in requests.
            If you wish to not specify this everytime, we
            reccomend either making an alias or inserting 
            your api key in the Main.java file in the 
            repository and building the jar.
            """,
            "--model",
            """
            The model name param sent to the api. The 
            default is 'gpt-3.5-turbo'. 
            """,
            "--tokens",
            """
            Sets the max tokens param sent to the api. 
            Defaults to 4096.
            """,
            "--temperature",
            """
            Sets the temperature param sent to the api.
            For tasks where you want more accurate answers
            This should be a value < 0.5. For more creative
            answer
            """
        );
        

        ArgParser parser = new ArgParser(
            args, 
            shortArgs, 
            longFlags, 
            null, 
            Map.of("-h", "View help", "--help", "View help"), 
            "******** terminal gpt ********",
            """
            Sends prompts to the OpenAi api and displays responses. Maintains a conversation history and allows fine user control of the api
            parameters.
                     
            """,
            """
            
            Exmaples:

                We provide a jar file. You can run the jar file with `java -jar target/chat-1.0.jar' or make an alias like this:
                `alias chat='java -jar /home/brandon/Documents/chat-terminal/target/chat-1.0.jar'`. You might also make an alias 
                like this to avoid having to submit your api-key each time you run the command: 
                `alias chat='java -jar /home/brandon/Documents/chat-terminal/target/chat-1.0.jar -k sk-proj-...'`
                
                I'll do the examples below with the first alias though.

                Basic:
                    `chat -k sk-proj-...`

                All options in file mode (order does not matter):
                    `chat -k sk-proj-... -m gpt-3.5-turbo -t 1024 -T 1.2`
            """,
            true
        );
        Map<String, String> parsedArgs = parser.parseArgs();
        if (parsedArgs.containsKey("-k")) {
            API_KEY = parsedArgs.get("-k");
        }
        else if (parsedArgs.containsKey("--api-key"))
            API_KEY = parsedArgs.get("--api-key");
        else if (parsedArgs.containsKey("-t")) {
            MAX_TOKENS = Integer.parseInt(parsedArgs.get("-t"));
        }
        else if (parsedArgs.containsKey("--tokens"))
            MAX_TOKENS = Integer.parseInt(parsedArgs.get("--tokens"));
        else if (parsedArgs.containsKey("-T")) {
            TEMPERATURE = Double.parseDouble(parsedArgs.get("-T"));
        }
        else if (parsedArgs.containsKey("--temperature"))
            TEMPERATURE = Double.parseDouble(parsedArgs.get("--temperature"));
        else if (parsedArgs.containsKey("-m")) {
            MODEL = parsedArgs.get("-m");
        }
        else if (parsedArgs.containsKey("--model="))
            MODEL = parsedArgs.get("--model");


        RestClient<String> client = new RestClient<>(
            (json, type, objectMapper) -> { // deserializer
                return Serializer.fromJson(json, Completion.class);
            },  
           (body, objectMapper) -> { // serializer
                String s = Serializer.json(body, true);
                // System.out.println(s);
                return s;
           }, 
           30000,  // timeout in milliseconds
           null
        );

        Map<String, String> headers = Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer " + API_KEY
        );

        TerminalTextEditor editor = new TerminalTextEditor();
        try {

            TerminalTextEditor.wipeTerminalCompletely();

            // main loop
            boolean quit = false;
            boolean enterToFinishMode = false;
            String messagesText = starterMessage;

            editor = new TerminalTextEditor();
            editor.init();
            int status = editor.sendInput(new ByteArrayInputStream(messagesText.getBytes()), false);

            while (!quit) {
                // editor loop
                editor.terminalResize(); // check for terminal resize
                InputStream in = System.in;
                status = 1;
                while(status != 0) {
                    status = editor.sendInput(in, enterToFinishMode);
                }
                editor.sendInput(new ByteArrayInputStream("\n\n\n".getBytes()), false);

                // get user input
                List<Message> messages = parseMessages(editor.textBuffer);
                messages = messages.subList(Math.max(0, messages.size() - HISTORY_LENGTH), messages.size());

                // check for quit command
                Message lastInput = messages.get(messages.size() - 1);
                if (isQuitting(lastInput)) {
                    quit = true;
                    break;
                }
                else if (enterToFinishMode(lastInput)) {
                    enterToFinishMode = !enterToFinishMode;
                    continue;
                }

                // make request to gpt
                Prompt promptObj = new Prompt();
                promptObj.model = MODEL;
                promptObj.max_tokens = MAX_TOKENS;
                promptObj.messages = messages;

                CompletableFuture<Completion> request = CompletableFuture.supplyAsync(() -> {
                    Completion completion = client.post(
                        API_URL, 
                        headers, 
                        promptObj, 
                        Prompt.class
                    );
                    return completion;
                });
                
                // Loop to check if the request is done
                while (!request.isDone()) {
                    try {
                        System.out.print(".");
                        System.out.flush();
                        Thread.sleep(500); // Sleep briefly to avoid busy-waiting
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                    }
                }
                Completion completion = request.get();

                // print out in file
                int terminalWidth = TerminalTextEditor.getTerminalWidth();
                Message message = completion.choices.get(0).message;
                StringBuilder messageText = new StringBuilder("");
                messageText.append(PROMPT_DIVIDER + Role.assistant.name() + "\n");
                String unescaped = unescapeMessage(message.content);
                unescaped = wrapMessage(
                    unescaped, 
                    terminalWidth
                );
                messageText.append(unescaped);
                messageText.append(starterMessage);

                // if the response is bigger than the terminal window reset the cursor to the top of the response
                int numLines = messageText.toString().split("\n").length;
                if (numLines > editor.terminalHeight) {
                    editor.addToBufferKeepingCursor(messageText.toString());
                    // int line = editor.getLine(editor.bufferPosition);
                    // editor.moveToLine(line + 2);
                }
                else {
                    editor.sendInput(new ByteArrayInputStream(messageText.toString().getBytes()), false);
                }
            }

            TerminalTextEditor.wipeTerminalCompletely();
            System.out.println(editor.textBuffer);
            
        }
        catch (GptExitException e) {
            System.out.println("Exiting...");
        } 
        catch( RestClient.RestClientException e) {
            e.printStackTrace();

            System.out.println();
            System.out.println("Status Code: " + e.getStatusCode());
            System.out.println("Response: \n" + e.getBody());
        }
        catch (Exception e) {
            e.printStackTrace();
        }finally {
            editor.cleanUp();
        }
    }

    private static boolean isQuitting(Message message) {


        String content = message.content.trim();

        return content.startsWith("quit");
    }

    private static boolean enterToFinishMode(Message message) {
        String content = message.content.trim();

        return content.startsWith("en");
    }

    private static Message getInputFromTerminalEditor() throws FileNotFoundException, IOException, InterruptedException {
        
        // Read the file
        File file = new File(FILE);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        // Read lines in from user in vim
        try {
            Message message = readMessage(reader, file);
            message.role = Role.user.name();
            return message;
        }
        catch(GptExitException e) {
            throw e;
        }
        finally {
            reader.close();
        }
    }

    public static class GptExitException extends RuntimeException { }

    public static List<Message> parseMessages(StringBuilder messagesText) throws FileNotFoundException, IOException{
        List<Message> parsedMessages = new ArrayList<>();

        String[] messages = messagesText.toString().split(PROMPT_DIVIDER);

        // read each message into history
        for (String message : messages) {
            if (!message.isBlank()) {

                Role role = message.startsWith(Role.user.name())? Role.user : Role.assistant;
                String content = message.substring(role.name().length());
                parsedMessages.add(
                    new Message(
                        content,
                        role.name()
                    )
                );

            }
        }

        return parsedMessages;
    }

    public static String messagesToString(List<Message> messages) throws IOException {
        StringBuilder messagesText = new StringBuilder("\n\n");
        for (int i = 0; i <  messages.size(); i++) {
            Message m = messages.get(i);
            messagesText.append(PROMPT_DIVIDER + m.role + "\n");
            String unescaped = unescapeMessage(m.content);
            messagesText.append(unescaped);
        }

        messagesText.append(starterMessage);
        return messagesText.toString();
    }

    public static String unescapeMessage(String message) {
        return message
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\"", "\"");
    }

    public static String wrapMessage(String message, int terminalWidth) {
        String[] lines = message.split("\n");

        StringBuilder wrappedMessage = new StringBuilder("\n\n");
        for (String line : lines) {
            if (line.length() > terminalWidth) {
                
                // chop line up into terminalWidth lengths
                while (line.length() > terminalWidth) {
                    
                    String substr = line.substring(0, terminalWidth);
                    
                    // move back to last word end
                    int i = substr.length() - 1;
                    while (i >= 0 && substr.charAt(i) != ' ') {
                        i--;
                    }
                    substr = substr.substring(0, i);

                    // add to buffer
                    wrappedMessage.append(substr).append("\n");

                    // remove from line
                    line = line.substring(substr.length());
                }
                wrappedMessage.append(line).append("\n");
            }
            else {
                wrappedMessage.append(line).append("\n");
            }
        }
        wrappedMessage.append("\n\n");

        return wrappedMessage.toString();
    }

    public static Message readMessage(BufferedReader reader, File file) throws IOException {
        String line;
        StringBuilder content = new StringBuilder();
        while ((line = reader.readLine()) != null && !line.startsWith(PROMPT_DIVIDER) ) {
            
            content.append(line).append("\n");

        }

        String role = line != null? line.replace(PROMPT_DIVIDER, "").trim() : null;

        return new Message(content.toString(), role);
    }



    // HELPER CLASSES

    public static class TerminalTextEditor {

        public int bufferPosition = 0;
        private StringBuilder textBuffer = new StringBuilder();
        private boolean ijklCursorMode = false;
        public boolean clearOnFinish = false;
        public OutputColorer colorer = new DefaultColorer();
        public int terminalHeight;
        public int terminalWidth;
        

        private int windowStartLine = 0;
        private int windowStartIndex = 0;

        // int startPosition = 
        public void run(String initialInput) throws IOException, InterruptedException {


            /*
             Interesting from 'man stty'

                [-]icanon
                        enable special characters: erase, kill, werase, rprnt

                [-]iexten
                        enable non-POSIX special characters

                [-]isig
                        enable interrupt, quit, and suspend special characters

             */


            try {
                this.init();
                
                int status = sendInput(new ByteArrayInputStream(initialInput.getBytes()), false);

                // main loop
                InputStream in = System.in;
                while(status != 0) {
                    status = sendInput(in, false);
                }
                
            } finally {
                this.cleanUp();
            }

            System.out.println("* Editor Exited *\n");
            // System.out.println(this.textBuffer);
        }

        public void terminalResize() {

            terminalHeight = TerminalTextEditor.getTerminalHeight();
            terminalWidth = TerminalTextEditor.getTerminalWidth();

        }

        public void init() throws IOException {

            terminalResize();

            try {
                // new ProcessBuilder("sh", "-c", "stty raw -echo </dev/tty").inheritIO().start().waitFor();
                new ProcessBuilder("sh", "-c", "stty -icanon min 1 -echo </dev/tty").inheritIO().start().waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            clearScreen();
        }

        public void cleanUp() throws IOException {
            // Restore terminal settings
            new ProcessBuilder("sh", "-c", "stty sane </dev/tty").inheritIO().start();
            TerminalTextEditor.reset();
        }

        public int sendInput(InputStream in, boolean enterToFinish) throws IOException {
            do {
                int ch = in.read();

                if (ch == 3) { // Ctrl + C to exit (for safety)
                    System.out.println("\nExiting...");
                    break;
                }

                // 27 10 is 'alt enter' on linux. Could be a good way to quit
                // 23 is 'ctrl backspace' on linux.
                // 27 100 is 'ctrl delete' on linux
                // 21 is 'ctrl delete' on mac.
                // 27 10 is 'option enter' on mac.
                // 226 136 134 is 'option j' on mac.
                // 27 106 is 'alt j' on linux

                // Escape sequence for arrow keys
                if (ch == 27 && System.in.available() > 0) { 
                    int mod = in.read();
                    if (mod == 91) { // '['
                        int arrow = in.read();
                        if (arrow == 68) { // Left arrow
                            this.moveLeft();
                        } else if (arrow == 67) { // Right arrow
                            this.moveRight();
                        }
                        else if (arrow == 65) { // up arrow
                            this.moveUp();
                        }
                        else if (arrow == 66) { // down arrow
                            this.moveDown();
                        }
                    }
                    else if (mod == 10 && !enterToFinish) { // alt/option enter
                        return finish();
                    }
                    continue;
                }
                else if (ch == 27) {
                    ijklCursorMode = !ijklCursorMode;
                }


                // enter in ijkl mode to exit
                if (ijklCursorMode && ch == 10) {
                    ijklCursorMode = false;
                    return finish();
                }

                // Backspace
                if (ch == 127 || ch == 8) { 
                    delete();
                    continue;
                }

                // Printable characters
                if (ch >= 32 && ch <= 126) { 

                    // ijkl cursor movements
                    if (ijklCursorMode) {
                        switch (ch) {
                            case 105 -> moveUp();
                            case 107 -> moveDown();
                            case 106 -> moveLeft();
                            case 108 -> moveRight();
                        };
                    }
                    else {
                        printChar((char) ch);
                    }
                }

                // tab
                if (ch == 9) {
                    printChar(' ');
                    printChar(' ');
                    printChar(' ');
                    printChar(' ');
                }

                // new lines
                if (isNewLineChar((char) ch)) {

                    if (enterToFinish) 
                        return finish();

                    // handle being on the last line of the window
                    scrollDownIfNeeded();

                    // reset start index since we're at the line start now
                    this.windowStartIndex = 0;

                    // if in a copy paste basically
                    if (in.available() > 0) {
                        printChar('\n');
                    }
                    // other wise adjust next line to tab of current line
                    else {
                        printChar('\n');
                        int count = 0;
                        int pos = this.bufferPosition;
                        pos -= 2;
                        if (pos > 0) {
                            // get to start of line
                            char c = this.textBuffer.charAt(pos);
                            while (!isNewLineChar(c) && pos > 0) {
                                pos--;
                                c = this.textBuffer.charAt(pos);
                            }
                            if (isNewLineChar(c)) {
                                pos++;
                                c = this.textBuffer.charAt(pos);
                            }

                            // count how many spaces
                            while (c == ' ') {
                                pos++;
                                count++;
                                c = this.textBuffer.charAt(pos);
                            }
                        }
                        
                        for (int i = 0; i < count; i++) {
                            printChar(' ');
                        }
                    }



                }

            } while (in.available() > 0);

            return 1;
        }

        private int finish() {
            if (clearOnFinish)
                TerminalTextEditor.clearScreen(); // reset cursor
            return 0;
        }


        private void printChar(char ch) {


            // insert char
            this.textBuffer.insert(this.bufferPosition, (char) ch);
            this.bufferPosition++;

            // handle getting to side of window
            int lineStart = getStartOfLine(this.bufferPosition);
            if ((this.bufferPosition - lineStart) >= this.terminalWidth) {
                this.windowStartIndex++;
            }
            
            reprint();
        }

        private void delete() {
            if (this.bufferPosition != 0) {
                this.textBuffer.delete(bufferPosition - 1, bufferPosition);
                this.bufferPosition--;
                reprint();
            }
        }

        private boolean isNewLineChar(char ch) {
            return ch == '\n' || ch == '\r';
        }

        private static void reset() {
            System.out.print("\u001B[0m");
            System.out.println("\033[1G");
        }
        
        private void moveLeft() {

            // handle getting to side of window
            if (this.windowStartIndex != 0) {
                int lineStart = getStartOfLine(this.bufferPosition);
                if ((this.bufferPosition - lineStart) < this.windowStartIndex) {
                    this.windowStartIndex--;
                }
            }
            
            // handle begining of line 
            if (this.bufferPosition != 0 && !isNewLineChar(this.textBuffer.charAt(this.bufferPosition - 1))) {
                this.bufferPosition--;
            }
            this.reprint();

        }

        private void moveRight() {


            // handle getting to side of window
            int lineStart = getStartOfLine(this.bufferPosition);
            if ((this.bufferPosition - lineStart) >= (this.terminalWidth - 1)) {
                this.windowStartIndex++;
            }

            if (this.bufferPosition != this.textBuffer.length() && !isNewLineChar(this.textBuffer.charAt(this.bufferPosition))) {
                this.bufferPosition++;
            }
            this.reprint();
        }

        private void moveUp() {

            // handle hitting top of window
            int currentLine = getLine(this.bufferPosition);
            if (this.windowStartLine > 0 && currentLine == this.windowStartLine) {
                this.windowStartLine--;
            }

            // determine distance from line start
            int lineStart = getStartOfLine(this.bufferPosition);
            int startToPos = this.bufferPosition - lineStart;

            if (lineStart > 0) {

                // move to next line
                lineStart--;

                // move to the beginning of the line
                int nextLineStart = getStartOfLine(lineStart);

                // move to the same index or end of new line
                this.bufferPosition = nextLineStart;
                int newLineLength = lineStart - nextLineStart;
                startToPos = startToPos > newLineLength? newLineLength : startToPos;
                int newLinePos = nextLineStart + startToPos;
                while (
                    this.bufferPosition < this.textBuffer.length() && 
                    this.bufferPosition < newLinePos ) this.bufferPosition++;
                
            }
            this.reprint();
        }

        private void moveDown() {


            // determine if we're on the last line
            boolean isLastLine = true;
            int i = this.bufferPosition;
            while (i < this.textBuffer.length() && isLastLine) {
                if (isNewLineChar(this.textBuffer.charAt(i))) isLastLine = false;
                i++;
            }

            if (!isLastLine) {
                // handle hitting window edge
                scrollDownIfNeeded();

                // determine start of current line
                int lineStart = getStartOfLine(this.bufferPosition);
                int startToPos = this.bufferPosition - lineStart;

                // move to next line
                lineStart = getEndOfLine(lineStart);
                lineStart++;

                // move to same index or end of new line
                this.bufferPosition = lineStart;
                int newLinePos = this.bufferPosition + startToPos;
                while (this.bufferPosition < this.textBuffer.length() && this.bufferPosition < newLinePos && !isNewLineChar(this.textBuffer.charAt(this.bufferPosition))) this.bufferPosition++;
                
                // handle moving down from line longer than window width
                int lineEnd = getEndOfLine(lineStart);
                if ((lineEnd - lineStart) < this.terminalWidth) this.windowStartIndex = 0;

                this.reprint();
            }
        }

        private void scrollDownIfNeeded() {

            // handle hitting bottom of window
            int line = getLine(this.bufferPosition);
            int lastLine = this.windowStartLine + this.terminalHeight - 1;
            if (line == lastLine) {
                this.windowStartLine++;
            }
        }

        public int getLine(int index) {
            int line = 0;
            for (int i = 0; i < index; i++) {
                if (isNewLineChar(this.textBuffer.charAt(i))) {
                    line++;
                }
            }

            return line;
        }

        private int getStartOfLine(int index) {

            if (index < this.textBuffer.length() && isNewLineChar(this.textBuffer.charAt(index))) index--;

            int lineStart = 0;
            for (int i = index; i >= 0; i--) {
                if (i < this.textBuffer.length() && isNewLineChar(this.textBuffer.charAt(i))) {
                    lineStart = i + 1;
                    break;
                }
            }
            return lineStart;
        }

        private int getEndOfLine(int index) {
            while (index < this.textBuffer.length() && !isNewLineChar(this.textBuffer.charAt(index))) index++;
            return index;
        }

        private static void cursorUp(int lines) {
            if (lines > 0)
                System.out.print("\033[" + lines + "A");
        }

        private static void cursorDown(int lines) {
            if (lines > 0)
                System.out.print("\033[" + lines + "B");
        }

        private static void cursorRight(int characters) {
            if (characters > 0)
                System.out.print("\033[" + characters + "C");
        }

        private static void cursorLeft(int characters) {
            if (characters > 0)
                System.out.print("\033[" + characters + "D");
        }

        private static void cursorHome() {
            System.out.print("\033[H");
            System.out.flush();
        }

        private static void clearBelowCursor() {
            System.out.println("\033[J");
        }

        private static void clearScreen() {
            System.out.print("\033[2J");
            System.out.flush();
            cursorHome();
        }

        private static void wipeTerminalCompletely() throws InterruptedException, IOException {
            new ProcessBuilder("clear").inheritIO().start().waitFor();
        }

        public static void moveCursor(int row, int column) {
            System.out.print("\033[" + row + ";" + column + "H");
        }

        public void addToBufferKeepingCursor(String content) {
            this.textBuffer.append(content);
            reprint();
        }

        private void reprint() {
            // XXX when buffer is larger than the screen we need to keep track of how far it is to the top.
            // since reseting the cursor only puts the cursor at the top current view

            // reset cursor to home and delete all lines
            clearScreen();

            // reprint
            int startIndex = getLineIndex(this.windowStartLine);
            int endIndex = getLineIndex(this.windowStartLine + this.terminalHeight);
            if (endIndex == -1) endIndex = this.textBuffer.length();
            if (endIndex < this.textBuffer.length() && isNewLineChar(this.textBuffer.charAt(endIndex))) endIndex--;
            

            StringBuilder coloredBuffer = colorer.addColors(
                this.textBuffer.substring(startIndex, endIndex)
            );
            String[] lines = coloredBuffer.toString().split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (this.windowStartIndex < line.length()) {
                    if ((this.terminalWidth + this.windowStartIndex) < line.length()) {
                        System.out.println(line.substring(this.windowStartIndex, this.terminalWidth + this.windowStartIndex));
                    }
                    else {
                        System.out.println(line.substring(this.windowStartIndex));
                    }
                }
                else {
                    System.out.println();
                }
            }

            // set cursor to current position
            // TerminalTextEditor.cursorHome();
            int cursorPos = startIndex;
            int linesToCursor = 0;
            int spacesToCursor = 0;
            while (cursorPos < this.bufferPosition) {
                if (cursorPos < this.textBuffer.length() && isNewLineChar(this.textBuffer.charAt(cursorPos))) {
                    spacesToCursor = 0;
                    linesToCursor++;
                }
                else {
                    spacesToCursor++;
                }
                cursorPos++;
            }
            moveCursor(linesToCursor + 1, spacesToCursor - this.windowStartIndex + 1);
            // TerminalTextEditor.cursorDown(linesToCursor);
            // TerminalTextEditor.cursorRight(spacesToCursor - this.windowStartIndex);
            
        }

        public int getLineIndex(int line) {
            if (line == 0) return 0;

            int count = 0;
            for (int i = 0; i < this.textBuffer.length(); i++) {
                if (isNewLineChar(this.textBuffer.charAt(i))) {
                    count++;
                }
                if (count == line) return i + 1;
            }

            return -1;

        }

        public static int getTerminalWidth() {
            int width = 80; // Default width
            try {
                Process process = new ProcessBuilder("sh", "-c", "tput cols 2> /dev/tty").start();
                process.waitFor();
                width = Integer.parseInt(new String(process.getInputStream().readAllBytes()).trim());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return width;
        }

        public static int getTerminalHeight() {
            int height = 24; // Default height
            try {
                Process process = new ProcessBuilder("sh", "-c", "tput lines 2> /dev/tty").start();
                process.waitFor();
                height = Integer.parseInt(new String(process.getInputStream().readAllBytes()).trim());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return height - 1;
        }

        private static int countLines(StringBuilder builder) {
            int lines = 0;
            for (int i = 0; i < builder.length(); i++) {
                if (builder.charAt(i) == '\n' || builder.charAt(i) == '\r') {
                    lines++;
                }
            }

            return lines;
        }

        public void moveToLine(int line) {
            this.windowStartIndex = 0;
            this.windowStartLine = line;

            int newBufferPosition = 0;
            int lines = 0;
            while (lines != line) {

                int nextLineStart = newBufferPosition;
                boolean foundNextLine = false;
                for (int i = newBufferPosition; i < this.textBuffer.length(); i++) {
                    if (isNewLineChar(this.textBuffer.charAt(i))) {
                        nextLineStart = i + 1;
                        foundNextLine = true;
                        break;
                    }
                }
                // if we hit the end of the buffer just set to the last line
                if (!foundNextLine) {
                    this.windowStartLine = lines;
                    break;
                }
                // otherwise 
                else {
                    newBufferPosition = nextLineStart;
                }

                lines++;
            }

            this.bufferPosition = newBufferPosition;
            reprint();
        }

        public static void main(String[] args) throws IOException, InterruptedException {
            // new TerminalTextEditor().run("\n\n\n\n\n\n\n\n\n\n\nend");
            new TerminalTextEditor().run("");
            // testChars();
            // test();
        }

        public static void testChars() throws IOException {

            try {
                new ProcessBuilder("sh", "-c", "stty -icanon min 1 -echo </dev/tty").inheritIO().start().waitFor();


                InputStream in = System.in;
                while (true) {
                    do {
                        int ch = in.read();
                        System.out.println(ch);
                    }
                    while (in.available() > 0);

                    System.out.println();
                }

                
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                // Restore terminal settings
                new ProcessBuilder("sh", "-c", "stty sane </dev/tty").inheritIO().start();
                TerminalTextEditor.reset();
            }



        }

        public static void test() {
            clearScreen();
            int h = getTerminalHeight();
            for (int i = 0; i < 50; i++) {
                System.out.println("asdfhasdf");
            }
            // cursorUp(50);
            
            clearScreen();
        }


        public interface OutputColorer {
            StringBuilder addColors(String buffer);
        }
        
        public static class DefaultColorer implements OutputColorer {

            @Override
            public StringBuilder addColors(String buffer) {
                return new StringBuilder(buffer);
            }


        }
    }

    public static class ArgParser {

        public static class HelpException extends RuntimeException {}

        public String[] args;
        public Map<String, String> shortFlags;
        public Map<String, String> longFlags;
        public Map<String, String> booleanFlags;
        public Map<String, String> helpFlags;
        public String appName;
        public String appDescription;
        public String notes;
        public boolean showManOnNoArgs;
    
        public ArgParser(
            String[] args, 
            Map<String, String> shortFlags, 
            Map<String, String> longFlags, 
            Map<String, String> booleanFlags, 
            Map<String, String> helpFlags, 
            String appName, 
            String appDescription, 
            String notes,
            boolean showManOnNoArgs
        )
        {
            this.args = args;
            this.shortFlags = shortFlags;
            this.longFlags = longFlags;
            this.booleanFlags = booleanFlags;
            this.helpFlags = helpFlags;
            this.appName = appName;
            this.appDescription = appDescription;
            this.notes = notes;
            this.showManOnNoArgs = showManOnNoArgs;
        }

        private Map<String, String> parseArgs() {
            if (shortFlags == null) shortFlags = new HashMap<>();
            if (booleanFlags == null) booleanFlags = new HashMap<>();
            if (longFlags == null) longFlags = new HashMap<>();
            
            try {

                if (showManOnNoArgs && args.length == 0) throw new HelpException();
    
                Map<String, String> parsedArgs = new HashMap<>();
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    
                    // short flags
                    if (shortFlags.containsKey(arg)) {
                        i++;
                        String nextValue = args[i];
                        parsedArgs.put(arg, nextValue);
                    }
                    // boolean flags
                    else if (booleanFlags.containsKey(arg)) {
                        parsedArgs.put(arg, "");
                    }
                    // long flags
                    else if(arg.contains("=")) {
                        String[] longArg = arg.split("=");
                        parsedArgs.put(longArg[0], longArg[1]);
                    }
                    else {
    
                        if (!helpFlags.containsKey(arg)) {
                            throw new IllegalArgumentException(arg + " is not a recognized argument");
                        }
                        else {
                            throw new HelpException();
                        }
    
                    }
                }
    
                return parsedArgs;
            }
            catch(Exception e) {
                printMan();
    
                if (e instanceof HelpException) {
                    throw e;
                }
                else {
                    if (e instanceof IllegalArgumentException) throw e;
        
                    throw new IllegalArgumentException("Error parsing args!");

                }
            }
            
    
        }
        
        private void printMan() {
            System.out.println(appName);
            System.out.println();
            System.out.println(appDescription);
            System.out.println();
            System.out.println("Args");
            Map<String, String> allFlags = new LinkedHashMap<>();
            if (booleanFlags != null) allFlags.putAll(booleanFlags);
            if (shortFlags != null) allFlags.putAll(shortFlags);
            if (longFlags != null) allFlags.putAll(longFlags);
            if (helpFlags != null) allFlags.putAll(helpFlags);
            int longestFlagName = allFlags.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length())
                .max()
                .orElse(0);
            for (Entry<String, String> entry : allFlags.entrySet()) {
                String description = "   " + entry.getKey() + " ".repeat(longestFlagName - entry.getKey().length()) + " " + entry.getValue();
                System.out.println(description);
                System.out.println();
            }

            System.out.println(notes);
        }
    }

    /**
     * A basic REST client that can make GET, POST, PUT, PATCH, and DELETE requests.
     * This client uses plain java and is contained in this single file. 
     * 
     * <p> To use this client you need to provide a deserializer and a serializer lambda functions.
     * Here's an example of how you could do that with the jackson objectmapper
     * <pre>
     * {@code
     * RestClient client = new RestClient(
     *     (json, type, objectMapper) -> { // deserializer
     *          JavaType javaType = objectMapper.getTypeFactory().constructType(type); 
     *          return objectMapper.readValue( json, javaType )
     *     },  
     *     (body, objectMapper) -> { // serializer
     *          return objectMapper.writeValueAsString(body);
     *     }, 
     *     30000,  // timeout in milliseconds
     *     new ObjectMapper() // object mapper
     * );
     * }
     * </pre>
     * 
     * To use the client you can do something like this:
     * <pre>
     * {@code
     * MyObject response = client.get("https://some-url.com/endpoint", Map.of("Content-Type", "application/json"), MyObject.class);
     * }
     * </pre>
     * 
     * Or for List or other collection types you should do this:
     * <pre>
     * {@code
     * List<MyObject> response = client.get("https://some-url.com/endpoint", Map.of("Content-Type", "application/json"), new ParamType<List<MyObject>>() {});
     * }
     * </pre>
     * 
     */
    public static class RestClient<S> {


        private DeserializeLambda<String, Type, S, Object, Exception> deserializer;
        private SerializeLambda<Object, S, String, Exception> serializer;
        private S serializerObject;
        private int timeout = 300000; // 5 minutes in milliseconds


        /**
         * Creates a simple REST client that can make GET, POST, PUT, PATCH, and DELETE requests.
         * 
         * <p> To create this client you need to provide a deserializer and a serializer lambda functions.
         * Here's an example of how you could do that with the jackson objectmapper
         * <pre>
         * {@code
         * RestClient client = new RestClient(
         *     (json, type, objectMapper) -> { // deserializer
         *          JavaType javaType = objectMapper.getTypeFactory().constructType(type); 
         *          return objectMapper.readValue( json, javaType )
         *     },  
         *     (body, objectMapper) -> { // serializer
         *          return objectMapper.writeValueAsString(body);
         *     }, 
         *     30000,  // timeout in milliseconds
         *     new ObjectMapper() // object mapper
         * );
         * }
         * </pre>
         * 
         */
        public RestClient(DeserializeLambda<String, Type, S, Object, Exception> deserializer, SerializeLambda<Object, S, String, Exception> serializer, int timeoutMillis, S serializerObject) {
            this.deserializer = deserializer;
            this.serializer = serializer;
            this.timeout = timeoutMillis;
            this.serializerObject = serializerObject;
        }


        public <T> T get(String url, Map<String, String> headers, Type responseClass) {
            return makeRequest(url, "GET", headers, null, responseClass);
        }

        public <T, B> T post(String url, Map<String, String> headers, B body, Type responseClass) {
            return makeRequest(url, "POST", headers, body, responseClass);
        }

        public <T, B> T put(String url, Map<String, String> headers, B body,  Type responseClass) {
            return makeRequest(url, "PUT", headers, body, responseClass);
        }

        public <T, B> T patch(String url, Map<String, String> headers, B body,  Type responseClass) {
            return makeRequest(url, "PATCH", headers, body, responseClass);
        }

        public <T, B> T delete(String url, Map<String, String> headers, B body, Type responseClass) {
            return makeRequest(url, "DELETE", headers, body, responseClass);
        }

        // javac -Xlint:unchecked src/main/java/brandon/gpt/Main.java
        // javac -Xlint:deprecation src/main/java/brandon/gpt/Main.java
        // javac -Xlint:unchecked -Xlint:deprecation src/main/java/brandon/gpt/Main.java
        public <T, B> T makeRequest(String url, String method, Map<String, String> headers, B body, Type responseClass) {
            
            HttpURLConnection conn = null;
            StringBuilder content = new StringBuilder();
            int statusCode = 0;
            try {
                // set up connection
                URL urlSpec = new URI(url).toURL();
                conn = (HttpURLConnection) urlSpec.openConnection();
                conn.setRequestMethod(method);
                conn.setReadTimeout(timeout);
                conn.setConnectTimeout(timeout);
        
                // Set headers
                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        conn.setRequestProperty(header.getKey(), header.getValue());
                    }
                }

                // Set body
                if (body != null) {
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        String bodyString = serializer.apply(body, serializerObject);
                        byte[] input = bodyString.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }

                // Read response
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }    
                } catch (IOException errorException) {
                    statusCode = conn.getResponseCode();
                    try (BufferedReader errorIn = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                        String inputLine;
                        while ((inputLine = errorIn.readLine()) != null) {
                            content.append(inputLine);
                        }
                    } catch (IOException e2) { 
                        System.out.println("THIS SHOULDN'T HAPPEN");
                    }
                }
                
                // Check status Code
                statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return (T) deserializer.apply(content.toString(), responseClass, serializerObject);
                }
            } catch (Exception e) {
                throw new RestClientException(statusCode, "Request failed", content.toString(), e);
            } 
            finally {
                // cleanup
                if (conn != null) {
                    conn.disconnect();
                }
            }
            throw new RestClientException(statusCode, "Request failed", content.toString(), null);
        }
        



        // HELPER CLASSES

        /**
         * Exception thrown by the rest client. Contains the status code and the body string
         * with the error response.
         */
        public static class RestClientException extends RuntimeException {
            private final int statusCode;
            private String body;

            public RestClientException(int statusCode, String message, String body, Exception cause) {
                super(message, cause);
                this.statusCode = statusCode;
                this.body = body;
            }

            public int getStatusCode() {
                return statusCode;
            }

            public String getBody() {
                return body;
            }
        }


        @FunctionalInterface
        public static interface DeserializeLambda<J, T, S, R, E extends Exception> {
            R apply(J json, T type, S serializer) throws E;
        }


        @FunctionalInterface
        public static interface SerializeLambda<O, S, R, E extends Exception> {
            R apply(O object, S serializer) throws E;
        }


        public static class ParamType<Q> implements Type {

            private final Type type;

            public ParamType() {
                Type genericSuperclass = getClass().getGenericSuperclass();
                ParameterizedType paramType = (ParameterizedType) genericSuperclass;
                this.type = paramType.getActualTypeArguments()[0];
            }


            public Type getType() {
                return this.type;
            }

        }

    }

    public static class Serializer {

        private Serializer() {}
    
    
        public static <T> String json(T object, boolean pretty) {
            if (object == null) return null;
            if (object instanceof String) return (String) object;
    
            if (object instanceof List) {
                List<?> list = (List<?>) object;
                List<Map<String, Object>> newList = new ArrayList<>();
                for (Object o : list) {
                    newList.add(mapify(o));
                }
                return convertListToJson(newList, pretty);
            }
    
            Map<String, Object> mapObject = mapify(object);
    
            if (pretty) {
                return mapToPrettyJsonString(mapObject);
            }
            else {
                return mapToJsonString(mapObject);
            }
        }
    
        public static <T> T fromJson(String json, Type type) {
            // remove whitespace
            json = removeWhitespaceFromJson(json);
            Class<T> clazz = (Class<T>) typeToClassWildcard(type);
    
            // if map or object
            if (json.charAt(0) == '{') {
                Map<String, Object> map = jsonStringToMap(json);
                
                if (Map.class.isAssignableFrom(clazz)) {
                    return safeCast(map, clazz);
                }
                else {
                    T object = getDefault(clazz);
                    return convertMapToObj(object, map);
                }
            }
            // if list
            else if (json.charAt(0) == '[') {
                // parse json into list of maps
                List<Map<String, Object>> value = (List<Map<String, Object>>) getObjectFromString(json);
    
                // convert maps into objects
                Type listType = ((ParameterizedType) type).getActualTypeArguments()[0];
                Class<?> listTypeClass = typeToClassWildcard(listType);
                List<Object> newList = new ArrayList<>();
                for (Map<String, Object> elem : value) {
                    if (Map.class.isAssignableFrom(listTypeClass)) {
                        newList.add(elem);
                    }
                    else {
                        Object converted = convertMapToObj(
                            getDefault(listTypeClass),
                            elem
                        );
                        newList.add(converted);
                    }
                }
    
                return safeCast(newList, clazz);
            }
            // if string
            else if (clazz == String.class) {
                return safeCast(json, clazz);
            }
            else {
                throw new SerializerException("Invalid json string", null);
            }
            
        }
    
        public static <T> T fromJson(String json, ParamType<T> type) {
            return fromJson(json, type.getType());
        }
    
    
    
        private static String convertListToJson(List<Map<String, Object>> mapifiedList, boolean pretty) {
            StringBuilder stringBuilder = new StringBuilder("[");
            if (pretty) {
                stringBuilder.append("\n");
                for (Map<String, Object> map : mapifiedList) {
                    String mapString = mapToPrettyJsonString(map);
                    String[] lines = mapString.split("\n");
                    for (String line : lines) {
                        stringBuilder.append("    ").append(line).append("\n");
                    }
                    stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                    stringBuilder.append(",\n");
                }
            }
            else {
                stringBuilder.append(" ");
                for (Map<String, Object> map : mapifiedList) {
                    String mapString = mapToJsonString(map);
                    stringBuilder.append(mapString).append(", ");
                }
            }
            
            stringBuilder.deleteCharAt(stringBuilder.length() - 2);
            stringBuilder.append("]");
    
            return stringBuilder.toString();
        }
    
        public static <T> T convertMapToObj(T object, Map<String, Object> map) {
    
            Field[] fields = object.getClass().getFields();
    
            for (Field field : fields) {
                
                try {
    
                    if (map.containsKey(field.getName())) {
                        boolean originalAccessibility = field.canAccess(object);
                        field.setAccessible(true);
    
                        Object value = map.get(field.getName());
                        Object convertedValue = convertObjectToType(value, field.getGenericType());
                        field.set(object, convertedValue);
    
                        field.setAccessible(originalAccessibility);
                    }
                    
                } catch (Exception e) {
                    throw new SerializerException("Error trying to create a(n) '" + object.getClass().getName() + "' object", e);
                } 
            }
    
            return object;
        }
    
        private static <T> T convertObjectToType(Object value, Type genericType) {
    
            Class<T> type = (Class<T>) typeToClassWildcard(genericType);
    
            // list type
            if (List.class.isAssignableFrom(type)) {
    
                // convert to a list
                List<Object> list = new ArrayList<>();
                List<?> resList = safeCast(value, List.class);
    
                // get the list type
                Type listType = Object.class;
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) genericType;
                    listType = parameterizedType.getActualTypeArguments()[0];
                }
    
                // iterate over the list and convert each object
                for (Object o : resList) {
                    Object val = convertObjectToType(o, typeToClassWildcard(listType));
                    list.add(val);
                }
                return safeCast(list, type);
            }
            else if (Map.class.isAssignableFrom(type)) {
                // convert to a map
                Map<Object, Object> map = new LinkedHashMap<>(); // linked to maintain order
                Map<?, ?> resMap = safeCast(value, Map.class);
    
                // get the map types
                Type keyType = Object.class;
                Type valueType = Object.class;
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) genericType;
                    keyType = parameterizedType.getActualTypeArguments()[0];
                    valueType = parameterizedType.getActualTypeArguments()[1];
                }
    
                // iterate over the map and convert each object
                for (Entry<?, ?> entry : resMap.entrySet()) {
                    Object key = convertObjectToType(entry.getKey(), typeToClassWildcard(keyType));
                    Object val = convertObjectToType(entry.getValue(), typeToClassWildcard(valueType));
                    map.put(key, val);
                }
                return safeCast(map, type);
            }
            // numbers
            else if (isNumericClass(type)) {
                boolean notBigDecimal = !(value instanceof BigDecimal);
                if (notBigDecimal) {
                    value = new BigDecimal(value.toString());
                }
    
                Object numberValue = convertBigDecimalToType(
                    safeCast(value, BigDecimal.class), 
                    type
                );
    
                return (T) numberValue;
            }
            // date types
            else if (Temporal.class.isAssignableFrom(type)) {
                return safeCast(
                    convertStringToDate(value.toString(), type),
                    type
                );
            }
            // boolean
            else if (isBoolean(type)) {
                return safeCast(value, type);
            }
            // string
            else if (type == String.class) {
                return safeCast(value, type);
            }
            // enum
            else if (type.isEnum()) {
                Object enumValue = Enum.valueOf((Class<Enum>) type, value.toString());
                return safeCast(enumValue, type);
            }
            // a user defined object
            else {
                T object = getDefault(type);
                return convertMapToObj(
                    object,
                    safeCastMap(value, String.class, Object.class)
                );
            }
        }
    
        private static Class<?> typeToClassWildcard(Type type) {
            if (type instanceof Class<?> clazz) {
                return clazz;
            }
            else if (type instanceof ParameterizedType parameterizedType) {
                return (Class<?>) parameterizedType.getRawType();
            }
            else {
                throw new SerializerException("Failed to convert type to class", null);
            }
        }
    
        private static <T> T getDefault(Class<T> type) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SerializerException("Missing no args constructor for type " + type.getName(), e);
            }
        }
    
    
        /**
         * Converts an object to a map ommiting null fields
         * 
         * @param object
         * @return
         */
        public static <T> Map<String, Object> mapify(T object) {
    
            if (object == null) return null;
            if (object instanceof Map objMap) {
                Object key = objMap.keySet().stream().findAny().orElse(null);
                if (key instanceof String) {
                    return safeCastMap(objMap, String.class, Object.class);
                }
            } 
    
            Map<String, Object> mappedResponse = new LinkedHashMap<>(); // linked to maintain order
            
            Class<?> objectType = object.getClass();
    
            Field[] fields = objectType.getFields();
    
            for (Field field : fields) {
                
                try {
                    // check for ignore tag
                    if (field.isAnnotationPresent(JsonIgnore.class)) continue;
    
                    // get the original accessibility and set accessible during the method
                    boolean originalAccessibility = field.canAccess(object);
                    field.setAccessible(true);
    
    
                    if (field.get(object) != null) {
    
                        Object value = null;
        
                        // check if the field is a list
                        if (List.class.isAssignableFrom(field.getType())) {
                            List<Object> list = new ArrayList<>();
    
                            Object fieldResponse = field.get(object);
    
                            List<?> resList = safeCast(fieldResponse, List.class);
                            for (Object o : resList) {
                                Object val = (isBasicJavaType(o.getClass()) || o.getClass().isEnum())? o : mapify(o);
                                list.add(val);
                            }
                            value = list;
                        }
                        // check if the field is a map
                        else if (Map.class.isAssignableFrom(field.getType())) {
                            Map<Object, Object> map = new LinkedHashMap<>();
    
                            Map<?, ?> resMap = safeCast(field.get(object), Map.class);
                            for (Entry<?, ?> entry : resMap.entrySet()) {
                                Object key = entry.getKey();
                                Object val = entry.getValue();
                                val = (isBasicJavaType(val.getClass()) || val.getClass().isEnum())? val : mapify(val);
                                map.put(key, val);
                            }
    
                            value = map;
                        }
                        // check if the field is a basic java type or enum
                        else if (isBasicJavaType(field.getType()) || field.getType().isEnum()) {
                            value = field.get(object);
                        }
                        // otherwise we need to fulfill it as well
                        else {
                            Object fieldResponse = field.get(object);
                            value = mapify(fieldResponse);
                        }
    
    
                        mappedResponse.put(field.getName(), value);
        
                    }
                    field.setAccessible(originalAccessibility);
                    
                } catch (IllegalArgumentException e) {
                    throw new SerializerException("Tried to map bad value to field '" + field.getName() + "' in static class '" + objectType.getName() + "'", e);
                }  catch (IllegalAccessException e) {
                    throw new SerializerException("Couldn't access field '" + field.getName() + "' in static class '" + objectType.getName() + "'", e);
                } 
            }
    
            return mappedResponse;
        }
    
        private static String mapToJsonString(Map<String, Object> map) {
            StringBuilder stringBuilder = new StringBuilder("{ ");
    
            for (Entry<String, Object> entry : map.entrySet()) {
    
                stringBuilder.append("\"").append(entry.getKey()).append("\" : ");
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> value = safeCast(entry.getValue(), Map.class);
                    String mapAsString = mapToJsonString(value);
                    stringBuilder.append(mapAsString);
                } 
                else if (entry.getValue() instanceof List) {
                    List<?> list = (List<?>) entry.getValue();
                    stringBuilder.append("[ ");
                    for (Object o : list) {
                        if (o instanceof Map) {
                            Map<String, Object> value = safeCast(o, Map.class);
                            String mapAsString = mapToJsonString(value);
                            stringBuilder.append(mapAsString);
                        } 
                        else if (o instanceof List) {
                            throw new SerializerException("Double nested lists aren't supported for jsonMap conversion. Map key: " + entry.getKey(), null);
                        }
                        else if (o instanceof String || o.getClass().isEnum()) {
                            appendStringOrEnum(
                                stringBuilder,
                                o,
                                false
                            );
                        }
                        else {
                            stringBuilder.append(entry.getValue());
                        }
                        stringBuilder.append(", ");
                    }
                    stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                    stringBuilder.append("]");
                }
                else {
                    if (entry.getValue() instanceof String || entry.getValue().getClass().isEnum()) {
                        appendStringOrEnum(
                            stringBuilder,
                            entry.getValue(),
                            false
                        );
                    }
                    else {
                        stringBuilder.append(entry.getValue());
                    }
                }
    
                stringBuilder.append(", ");
            }
    
            stringBuilder.deleteCharAt(stringBuilder.length() - 2);
            stringBuilder.append("}");
    
            return stringBuilder.toString();
        }
    
        /**
         * Converts a map to a pretty json string
         */
        private static String mapToPrettyJsonString(Map<String, Object> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("{\n");
    
            for (Entry<String, Object> entry : map.entrySet()) {
    
                if (entry.getValue() == null) {
                    builder.append("    \"").append(entry.getKey()).append("\": null,\n");
                }
                else if (entry.getValue() instanceof Map) {
                    builder.append("    \"").append(entry.getKey()).append("\": ");
                    String mapString = mapToPrettyJsonString((Map<String, Object>) entry.getValue());
                    String[] lines = mapString.split("\n");
    
                    builder.append(lines[0]).append("\n");
                    String[] otherLines = Arrays.copyOfRange(lines, 1, lines.length);
                    for (String line : otherLines) {
                        builder.append("    ").append(line).append("\n");
                    }
                    builder.deleteCharAt(builder.length() - 1);
                    builder.append(",\n");
    
                } 
                else if (entry.getValue() instanceof List) {
                    builder.append("    \"").append(entry.getKey()).append("\": ");
                    List<?> list = (List<?>) entry.getValue();
                    builder.append("[\n");
                    for (Object o : list) {
                        if (o instanceof Map) {
                            String mapString = mapToPrettyJsonString((Map<String, Object>) o);
                            String[] lines = mapString.split("\n");
    
                            builder.append("        ").append(lines[0]).append("\n");
                            String[] otherLines = Arrays.copyOfRange(lines, 1, lines.length);
                            for (String line : otherLines) {
                                builder.append("        ").append(line).append("\n");
                            }
                            builder.deleteCharAt(builder.length() - 1);
                            builder.append(",\n");
                        } 
                        else if (o instanceof List) {
                            throw new SerializerException("Double nested lists aren't supported for jsonMap conversion. Map key: " + entry.getKey(), null);
                        }
                        else if (o instanceof String || o.getClass().isEnum()) {
                            builder.append("    ").append("    ");
                            appendStringOrEnum(
                                builder,
                                o,
                                true
                            );
                        }
                        else {
                            builder.append(entry.getValue()).append(",\n");
                        }
                    }
                    builder.deleteCharAt(builder.length() - 2);
                    builder.append("    ],\n");	
                }
                else {
                    builder.append("    \"").append(entry.getKey()).append("\": ");
                    if (entry.getValue() instanceof String || entry.getValue().getClass().isEnum()) {
                        appendStringOrEnum(
                            builder,
                            entry.getValue(),
                            true
                        );
                    }
                    else {
                        builder.append(entry.getValue()).append(",\n");
                    }
                }
    
            }
    
            if (!map.isEmpty()) builder.deleteCharAt(builder.length() - 2);
            else builder.deleteCharAt(builder.length() - 1);
            builder.append("}");
    
            return builder.toString();
        }
    
        private static void appendStringOrEnum(StringBuilder builder, Object value, boolean newLine) {
            String valueString = escapeCharacters(
                value.toString()
            );
            builder.append("\"").append(valueString).append("\"");
            if (newLine) builder.append(",\n");
        }
        
        private static Map<String, Object> jsonStringToMap(String json) {
    
            // "{"ham":{"cheese":1,"list":[1,2,3]}}"
            // "ham":{"cheese":1,"list":[1,2,3]}
    
            Map<String, Object> map = new LinkedHashMap<>(); // linked to maintain order
    
            int i = 1;
            while (i < json.length()-1) {
    
                // get key
                String key = "";
                if (isNonEscapedQuote(json, i)) {
                    int j = jumpToEndOfQuote(json, i+1);
                    
                    key = json.substring(i+1, j);
                    i = j+1;
                }
    
                // get value
                String valueString = "";
                if (json.charAt(i) == ':') {
                    int j = i+1;
                    if (json.charAt(j) == '{') {
                        int openBrackets = 1;
                        while (openBrackets > 0) {
                            j++;
                            if (isNonEscapedQuote(json, j)) {
                                j = jumpToEndOfQuote(json, j);
                            }
                            if (json.charAt(j) == '{') openBrackets++;
                            if (json.charAt(j) == '}') openBrackets--;
                        }
                    }
                    else if (json.charAt(j) == '[') {
                        int openBrackets = 1;
                        while (openBrackets > 0) {
                            j++;
                            if (isNonEscapedQuote(json, j)) {
                                j = jumpToEndOfQuote(json, j);
                            }
                            if (json.charAt(j) == '[') openBrackets++;
                            if (json.charAt(j) == ']') openBrackets--;
                        }
                    }
                    else if (isNonEscapedQuote(json, j)) {
                        j++;
                        while (!isNonEscapedQuote(json, j))  {
                            j++;
                        }
                    }
                    else {
                        while (json.charAt(j) != ',' && json.charAt(j) != '}') j++;
                        j--;
                    }
                    valueString = json.substring(i+1, j+1);
                    i = j+1;
                }
                Object value = getObjectFromString(valueString);
    
                i++;
                map.put(key, value);
            }
    
    
            return map;
        }
    
        public static Object getObjectFromString(String valueString) {
            if (valueString.equals("null")) return null;
    
            Object value;
            if (valueString.charAt(0) == '{') {
                value = jsonStringToMap(valueString);
            }
            else if (valueString.charAt(0) == '[') {
                // remove outer brackets
                valueString = valueString.substring(1, valueString.length()-1);
                
                // if not empty process
                List<Object> list = new ArrayList<>();
                if (!valueString.isEmpty()) {
    
                    // split by commas
                    List<String> values = new ArrayList<>();
                    int openBrackets = 0;
                    int j = 0;
                    for (int i = 0; i < valueString.length(); i++) {
        
                        if (isNonEscapedQuote(valueString, i)) i = jumpToEndOfQuote(valueString, i);
                        if (valueString.charAt(i) == '[' || valueString.charAt(i) == '{') openBrackets++;
                        if (valueString.charAt(i) == ']' || valueString.charAt(i) == '}') openBrackets--;
        
                        if (valueString.charAt(i) == ',' && openBrackets == 0) {
                            values.add(valueString.substring(j, i));
                            j = i+1;
                        }
                    }
                    values.add(valueString.substring(j));
        
                    // convert each value
                    for (String val : values) {
                        list.add(getObjectFromString(val));
                    }
                }
                value = list;
            }
            else if (isNonEscapedQuote(valueString, 0)) {
                value = valueString.substring(1, valueString.length()-1);
            }
            else if ("true".equals(valueString) || "false".equals(valueString)) {
                value = Boolean.parseBoolean(valueString);
            }
            else {
                value = new BigDecimal(valueString);
            }
    
            return value;
        }
    
        public static Object convertBigDecimalToType(BigDecimal number, Class<?> numberType) {
            if (numberType == int.class || numberType == Integer.class) {
                return number.intValue();
            }
            else if (numberType == long.class || numberType == Long.class) {
                return number.longValue();
            }
            else if (numberType == double.class || numberType == Double.class) {
                return number.doubleValue();
            }
            else if (numberType == float.class || numberType == Float.class) {
                return number.floatValue();
            }
            else if (numberType == short.class || numberType == Short.class) {
                return number.shortValue();
            }
            else if (numberType == byte.class || numberType == Byte.class) {
                return number.byteValue();
            }
            else if (numberType == BigDecimal.class) {
                return number;
            }
            else {
                throw new SerializerException("Can't convert BigDecimal to type " + numberType.getName(), null);
            }
        }
    
        private static Object convertStringToDate(String dateString, Class<?> dateType) {
            try {
                if (dateType == ZonedDateTime.class) {
                    return ZonedDateTime.parse(dateString);
                }
                else if (dateType == OffsetDateTime.class) {
                    return OffsetDateTime.parse(dateString);
                }
                else if (dateType == LocalDateTime.class) {
                    return LocalDateTime.parse(dateString);
                }
                else if (dateType == LocalDate.class) {
                    return LocalDate.parse(dateString);
                }
                else {
                    throw new SerializerException(dateType.getName() + " isn't a supported date type use these instead (ZonedDateTime, OffsetDateTime, LocalDateTime, LocatDate)", null);
                }
            }
            catch (DateTimeParseException e) {
                throw new SerializerException("Failed to parse date string '" + dateString + "' to type " + dateType.getName(), e);
            }
        }
    
    
    
    
        /**
         * Determines if a type is one of the following:
         * - String
         * - Date types
         * - Numeric Types
         * @param type
         * @return
         */
        private static boolean isBasicJavaType(Class<?> type) {
            return isNumericClass(type) || Temporal.class.isAssignableFrom(type) || type == String.class || isBoolean(type);
        }
    
        private static boolean isBoolean(Class<?> type) {
            return type == Boolean.class || type == boolean.class;
        }
    
        private static boolean isNumericClass(Class<?> type) {
            return Number.class.isAssignableFrom(type) || isPrimitiveNumericClass(type);
        }
    
        private static boolean isPrimitiveNumericClass(Class<?> type) {
            return type == int.class || type == long.class || type == double.class
                    || type == float.class || type == short.class || type == byte.class;
        }
    
        /**
         * Tries to cast the object to the desired type. Throwing an exception if not possible.
         *
         * @param obj object to cast
         * @param desiredType type to cast too
         * @return cast object of type T
         * @param <T> type
         */
        private static <T> T safeCast(Object obj, Class<T> desiredType) {
            if (obj == null) return null;
    
            if (desiredType.isInstance(obj)) {
                return desiredType.cast(obj);
            }
            String message = "Failed trying to cast class " + obj.getClass().getName() + " to " + desiredType.getName();
            throw new SerializerException(message, null);
        }
    
    
        /**
         * Tries to cast the map to the desired type. Throwing an exception if not possible.
         *
         * @param obj map to cast
         * @param keyType type to cast keys too
         * @param valueType type to cast values too
         * @return cast map of type Map<T,S>
         * @param <T> type
         * @param <S> type
         */
        public static <T, S> Map<T,S> safeCastMap(Object obj, Class<T> keyType, Class<S> valueType) {
            try {
                Map<T, S> newMap = new LinkedHashMap<>();
                for(Object entry : safeCast(obj, Map.class).entrySet()) {
                    if (entry instanceof Map.Entry<?, ?> newEntry) {
                        newMap.put(
                            safeCast(newEntry.getKey(), keyType),
                            safeCast(newEntry.getValue(), valueType)
                        );
                    }
                }
                return newMap;
            } catch(Exception e){
                String message = "Failed trying to cast class " + obj.getClass().getName()
                    + " to map of " + keyType.getName() + ", " + valueType.getName();
                throw new SerializerException(message, null);
            }
        }
    
        /**
         * Tries to cast the list to the desired type. Throwing an exception if not possible.
         *
         * @param list list to cast
         * @param type type to cast keys too
         * @return cast list of type List<T>
         * @param <T> type
         */
        public static <T> List<T> safeCastList(List<?> list, Class<T> type) {
            try {
                List<T> newList = new ArrayList<>();
                if (list == null) {
                    return newList;
                }
                for(Object item : list) {
                    newList.add(
                        safeCast(item, type)
                    );
                }
                return newList;
            } catch(Exception e) {
                String message = "Failed trying to cast class " + list.getClass().getName()
                    + " to list of " + type.getName();
                throw new SerializerException(message, e);
            }
    
        }
    
        /**
         * Removes \n\t and whitespace anywhere in the json other than in strings.
         * Cleans up spare quotes in strings.
         */
        public static String removeWhitespaceFromJson(String json) {
            StringBuilder builder = new StringBuilder();
            boolean inString = false;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\"') {
                    if (inString) {
                        if (!isEscapedQuoteOrShouldBe(json, i)) {
                            inString = false;
                        }
                        else {
                            builder.append("\\\"");
                            continue;
                        }
                    }
                    else inString = true;
                }
                if (c == ' ' || c == '\n' || c == '\t') {
                    if (inString) {
                        builder.append(c);
                    }
                }
                else {
                    builder.append(c);
                }
            }
            return builder.toString();
        }
    
        public static boolean isNonEscapedQuote(String json, int index) {
            return json.charAt(index) == '\"' && (index == 0 || json.charAt(index-1) != '\\');
        }
    
        private static List<Character> whiteSpace = List.of(' ', '\n', '\t');
        private static List<Character> quoteIndicators = List.of(',', '}', ']', ':');
        public static boolean isEscapedQuoteOrShouldBe(String json, int index) {
            if (json.charAt(index) == '\"') {
                if (index > 0 && json.charAt(index-1) == '\\') {
                    return true;
                }
                else {
                    index++;
                    char c = json.charAt(index);
                    while (whiteSpace.contains(c)) {
                        index++;
                        c = json.charAt(index);
                    }
                    return !quoteIndicators.contains(c);
                }
            }
            return false;
        }
    
        public static int jumpToEndOfQuote(String json, int index) {
            index++;
            while (!isNonEscapedQuote(json, index)) {
                index++;
            }
            return index;
        }
    
    
        /**
         * Escape quotes in strings
         */
        public static String escapeCharacters(String json) {
    
            // escape quotes
            json = json
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\\\"", "\"");
    
            // make sure quotes are escaped
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\"' && !(i > 0 && json.charAt(i-1) == '\\')) {
                    builder.append("\\\"");
                }
                else {
                    builder.append(c);
                }
            }
    
            return builder.toString();
        }
    
        /**
         * Annotation that can be used to ignore fields when serializing to json.
         * 
         * <p>Does not prevent the field being set when deserializing.
         */
        @Documented
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.FIELD})
        public static @interface JsonIgnore {}
        
    
        public static class SerializerException extends RuntimeException {
            public SerializerException(String message, Throwable cause) {
                super(message, cause);
            }
        }
    
        public abstract static class ParamType<T> {
            private final Type type;
    
            protected ParamType() {
                Type superClass = getClass().getGenericSuperclass();
                if (superClass instanceof ParameterizedType) {
                    this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
                } else {
                    throw new IllegalArgumentException("TypeReference must be parameterized");
                }
            }
    
            public Type getType() {
                return this.type;
            }
    
        }
    }
    
    public enum AnsiControl {

        
        RESET("\u001B[0m"),
        CLEAR_SREEN("\u001B[2J\u001B[H"),
        RESET_FONT_SIZE("\u001B[0m"),
        SIZE("\u001B[=18h"),
        SET_FONT_SIZE("\u001B[");

        
        // public static String CLEAR_SREEN = "\u001B[2J\u001B[H";
        // public static String RESET_FONT_SIZE = "\u001B[0m";
        // public static String SET_FONT_SIZE = "\u001B[";


        private final String code;
        AnsiControl(String code) {
            this.code = code;
        }


        public static void setCursor(int x, int y) {
            System.out.print("\u001B[" + y + ";" + x + "H");
        }

        public static String color(int r, int g, int b) {
            return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
        }

        public static String color(int x) {
            return "\u001B[38;5;" + x + "m";
        }

        public static String background(int r, int g, int b) {
            return "\u001B[48;2;" + r + ";" + g + ";" + b + "m";
        }

        public static String background(int x) {
            return "\u001B[48;5;" + x + "m";
        }


        @Override
        public String toString() {
            return code;
        }
    }

    // DTOS
    // [
    //     {"role": "system", "content": "You are a helpful assistant."},
    //     {"role": "user", "content": "Earlier we talked about European capitals. Can you now tell me about Germany?"},
    //     {"role": "assistant", "content": "Sure! The capital of Germany is Berlin."}
    // ]

    public static class Prompt {
        public String model;
        public List<Message> messages;
        public int max_tokens = 100;
        public double temperature = 0.7f;

    }
    public enum Role {
        system, user, assistant
    }
    public static class Message {
        public String role;
        public String content;

        public Message() {}

        public Message(String content, String role) {
            this.content = content;
            this.role = role;
        }
    }


    /*
    {
        "id": "chatcmpl-xyz",
        "object": "chat.completion",
        "created": 1615860523,
        "model": "gpt-3.5-turbo-0301",
        "usage": {
            "prompt_tokens": 10,
            "completion_tokens": 20,
            "total_tokens": 30
        },
        "choices": [
            {
            "message": {
                "role": "assistant",
                "content": "The capital of France is Paris."
            },
            "finish_reason": "stop",
            "index": 0
            }
        ]
    }
     */
    public static class Completion {
        public String id;
        public String object;
        public long created;
        public String model;
        public List<Choice> choices;

    }

    public static class Choice {
        public Message message;
        public String finish_reason;
        public int index;
    }


}
