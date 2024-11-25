import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import brandon.gpt.Main;

public class Test {

    public static void main(String[] args) throws IOException {

        // String json = """
        // {
        //     "message": {
        //         "role": "assistant",
        //         "content": "Sure! One way you can set up a custom hotkey for saving and exiting in vim is by adding the following lines to your `.vimrc` file:\n\n```\n\" Map Ctrl-s to save and exit\nnnoremap <C-s> :wq<CR>\n```\n\nThis will allow you to press `Ctrl + s` to save and exit vim. Just make sure to save and reload your `.vimrc` file for the changes to take effect.",
        //         "refusal": null
        //     }
        // }
        // """;
        String json = """
         " \"
        """;
        // Main.Completion comp = Main.Serializer.fromJson(json, Main.Completion.class);
        // System.out.println(comp.choices.get(0).message.content);



        // Files.writeString(Path.of("j.json"), json);
        // Files.writeString(Path.of("j.json"), json.replaceAll("\"", "\\\""));

        StringBuilder builder = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            int b = json.charAt(i);
            char c = json.charAt(i);
            if (c == '\"') {
                inString = !inString;
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
        

        System.out.println(builder.toString());
    }
}
