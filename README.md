# chat-terminal
```
USAGE: chat [OPTION]...
Sends prompts to the OpenAi api and displays responses. Maintains a conversation history and allows fine user control of the api
parameters.

Works in two main modes, file based mode, and terminal mode.

Terminal Mode
- Classic mode where user prompts are entered into the terminal and then submitted by hitting 'enter'. 
- Api responses are then displayed
- The prompt will loop until the user submits 'quit', 'exit', or 'close'.
- The user can enter 'dump' to dump the chat history into a 'chat.md' file in the current directory. This 
file will be ready to use in file mode

File mode
- Mode useful for editing prompts to the api in a text editor. Useful for editing code blocks to be sent to the api. 
- In this mode a 'chat.md' file is created in the current directory. Api responses and user prompts are displayed from most
recent to oldest seperated by dividers. 
- Past responses or prompts can be edited in this mode allowing control of the history. Each time a request is made in this mode,
the history is refreshed from the contents of the 'chat.md' file. 
- Each time the user wants to submit the most recent prompt in the file, they should run the 'chat -f' command again to send the 
prompt to the api.


Options (order does not matter):
    -f, --file-mode                         Activates file mode explained above
    -?, --help                              Prints this dialog
    -k, --api-key=sk-proj-...               Sets the open-api key to be used in requests.
                                            If you wish to not specify this everytime, we
                                            reccomend either making an alias or inserting 
                                            your api key in the Main.java file in the 
                                            repository and building the jar with `mvn package`
    -m, --model=...                         The model name param sent to the api. The 
                                            default is 'gpt-3.5-turbo'. 
    -t, --tokens=...                        Sets the max tokens param sent to the api. 
                                            Defaults to 4096.
    -T, --temperature=...                   Sets the temperature param sent to the api.
                                            For tasks where you want more accurate answers
                                            This should be a value < 0.5. For more creative
                                            answers this can be more, even 1.0+.


Exmaples:

    We provide a jar file. You can run the jar file with `java -jar chat-1.0.jar' or make an alias like this:
    `alias chat='java -jar /home/brandon/Documents/chat-terminal/chat-1.0.jar'`. You might also make an alias 
    like this to avoid having to submit your api-key each time you run the command: 
    `alias chat='java -jar /home/brandon/Documents/chat-terminal/chat-1.0.jar -k sk-proj-...'`
    
    I'll do the examples below with the first alias though.

    Basic:
        `chat -k sk-proj-...`

    All options in file mode (order does not matter):
        `chat -k sk-proj-... -f -m gpt-3.5-turbo -t 1024 -T 1.2`

                

```

# Building
First open Main.java and replace the 'API_KEY' variable with your open ai api key.

To build the app, run the following command in the root directory of the project:
```
mvn clean package
```

You'll need mvn and Java 17 installed


# Usage
I personally prefer the default mode. I use vscode as my editor and I have a keybinding to run the app and send my 'chat.md' file to the api. Here's the keybinding I have in my keybindings.json file:
```
{
    "key": "cmd+g",
    "command": "workbench.action.terminal.sendSequence",
    "args": {
        "text": "java -jar ~/Documents/chat/target/chat-1.0.jar -f\u000D"
    }
}
```
(the characters on the end of the text string are the enter key)

So basically I can edit the top of the file with my next prompt, hit cmd+g, and then the response from chat gpt will be appended to the top of the 'chat.md' file in the directory. 

# Notes
Sorry about the reverse nature of the chat.md. Not sure why I choose to append the responses to the top of the file, but if enough people would prefer it the other way I could totally change that.

Also you can edit your chat.md file in almost any way you want. So you can delete your history, edit it, or delete some of the most recent responses if you like.

The history in the file is also all the history that is transmitted to the api each request. So you can see for yourself what history chat gpt has available when generating a response.

Anyway, free to use commericially or otherwise. Enjoy!


