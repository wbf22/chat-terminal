import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import brandon.gpt.Main;
import brandon.gpt.Main.Choice;
import brandon.gpt.Main.Prompt;

public class Test {

    public static void main(String[] args) throws IOException {

        // String json = """
        // {
        //     "message": {
        //         "role": "assistant",
        //         "content": "Sure! You can add the following lines to your .vimrc file to map a new hotkey for saving and exiting in vim:\n\n```\n\" Map <leader>s to save and exit\nnnoremap <Leader>s :wq<CR>\n```\n\nWith this mapping in place, you can use the \\s hotkey combination to save and exit vim. Just substitute '\\s' with any key you prefer. Let me know if you need further assistance.",
        //         "refusal": null
        //     }
        // }
        // """;
        // Main.Choice comp = Main.Serializer.fromJson(json, Main.Choice.class);

        // Main.Completion comp = Main.Serializer.fromJson(json, Main.Completion.class);
        // // System.out.println(comp.message.content);

        // String ser = Main.Serializer.json(comp, true);
        // System.out.println(ser);


        // Prompt promptObj = new Prompt();
        // promptObj.model = "gpt-3.5-turbo";
        // promptObj.max_tokens = Main.MAX_TOKENS;
        // promptObj.messages = Main.readHistory();

        // Main.writeHistory(promptObj.messages);


        // String json = Main.Serializer.json(promptObj, true);
        // System.out.println(json);


        String input = "This is a string with escaped quotes: \\\"Hello, World!\\\"";
        System.out.println("Original: " + input);

        // Replace escaped quotes with normal quotes
        String output = input.replace("\\\"", "\"");
        System.out.println("Processed: " + output);

    }
}
