# chat-terminal
A terminal app for talking with chat gpt. 

There are two modes:

1. Default (java -jar {path to root dir}/chat-1.0.jar):
 - Running the app looks for the 'chat.md' file in the current directory and uses it as the chat history. It will then send that to the open ai chat gpt api and get a response, saving it back to the 'chat.md' file.


2. Vim (java -jar {path to root dir}/chat-1.0.jar -vim):
 - Runs in a loop, opening the 'chat.md' file in vim, waiting for the user to save and exit, hitting the api and rewriting the file, then opening the file in vim again.


# Building
To build the app, run the following command in the root directory of the project:
```
mvn clean package
```

You'll need mvn and Java 17 installed
