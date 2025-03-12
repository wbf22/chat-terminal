import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import brandon.gpt.Main;
import brandon.gpt.Main.Choice;
import brandon.gpt.Main.Prompt;

public class Test {

    public static void main(String[] args) throws IOException {

        String json = """
        {
            "id": "chatcmpl-BAGqxd4kQj5HuGHLgheJheO6lrpyc",
            "object": "chat.completion",
            "created": 1741787763,
            "model": "gpt-3.5-turbo-0125",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello! How can I assist you today?",
                        "refusal": null,
                        "annotations": []
                    },
                    "logprobs": null,
                    "finish_reason": "stop"
                }
            ],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 10,
                "total_tokens": 20,
                "prompt_tokens_details": {
                    "cached_tokens": 0,
                    "audio_tokens": 0
                },
                "completion_tokens_details": {
                    "reasoning_tokens": 0,
                    "audio_tokens": 0,
                    "accepted_prediction_tokens": 0,
                    "rejected_prediction_tokens": 0
                }
            },
            "service_tier": "default",
            "system_fingerprint": null
        }
        """;
        Main.Completion comp = Main.Serializer.fromJson(json, Main.Completion.class);

        String ser = Main.Serializer.json(comp, true);
        System.out.println(ser);


        // "{"id":"chatcmpl-BAGqxd4kQj5HuGHLgheJheO6lrpyc","object":"chat.completion","created":1741787763,"model":"gpt-3.5-turbo-0125","choices":[{"index":0,"message":{"role":"assistant","content":"Hello! How can I assist you today?","refusal":null,"annotations":[]},"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":10,"total_tokens":20,"prompt_tokens_details":{"cached_tokens":0,"audio_tokens":0},"completion_tokens_details":{"reasoning_tokens":0,"audio_tokens":0,"accepted_prediction_tokens":0,"rejected_prediction_tokens":0}},"service_tier":"default","system_fingerprint":null}"

    }
}
