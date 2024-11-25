# chat-terminal
A terminal app for talking with chat gpt. 

There are two modes:

1. Default (java -jar {path to root dir}/chat-1.0.jar):
 - Running the app looks for the 'chat.md' file in the current directory and uses it as the chat history. It will then send that to the open ai chat gpt api and get a response, saving it back to the 'chat.md' file.


2. Vim (java -jar {path to root dir}/chat-1.0.jar -vim):
 - Runs in a loop, opening the 'chat.md' file in vim, waiting for the user to save and exit, hitting the api and rewriting the file, then opening the file in vim again.


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
        "text": "java -jar ~/Documents/chat/target/chat-1.0.jar\u000D"
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


