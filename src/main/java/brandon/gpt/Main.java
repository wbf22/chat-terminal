package brandon.gpt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import brandon.gpt.Main.Serializer.ParamType;

public class Main {

    public static int MAX_TOKENS = 4096;
    public static String PROMPT_DIVIDER = "----------------------------------------";
    public static List<String> EXIT_COMMANDS = List.of("exit", "quit", "close");
    public static String FILE = "chat.md";
    public static String MODEL = "gpt-3.5-turbo";
    public static final String API_URL = "https://api.openai.com/v1/chat/completions";
    public static final String API_KEY = "";  // Replace with your actual OpenAI API key
    public static final int HISTORY_LENGTH = 20;


    public static void main(String[] args) {
        boolean vimMode = args.length > 0 && args[0].equals("-vim");

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

        try {
            List<Message> history = readHistory();

            do {
                // get user input
                Message msg = getInput(vimMode);
                history.add(msg);

                // make request to gpt
                System.out.println("Sending prompt to GPT...");
                Prompt promptObj = new Prompt();
                promptObj.model = MODEL;
                promptObj.max_tokens = MAX_TOKENS;
                promptObj.messages = history;

                Completion completion = client.post(
                    API_URL, 
                    headers, 
                    promptObj, 
                    Prompt.class
                );

                // print out in file
                Message message = completion.choices.get(0).message;
                message.content = "\n\n" + message.content + "\n\n";
                history.add(message);
                history = history.subList(Math.max(0, history.size() - HISTORY_LENGTH), history.size());
                
                writeHistory(history);
                history = readHistory();

            } 
            while (vimMode);
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
        }
    }

    private static Message getInput(boolean inVim) throws FileNotFoundException, IOException, InterruptedException {
        
        if (inVim) {
            // Command to open Vim
            ProcessBuilder processBuilder = new ProcessBuilder("vim", FILE);
            processBuilder.inheritIO(); // Pass the terminal IO to Vim
    
            // Start Vim and to exit
            Process process = processBuilder.start();
            process.waitFor();
        }

        // Read the file
        File file = new File(FILE);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        // Read lines in from user in vim
        try {
            Message message = readMessage(reader, file, inVim);
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

    public static List<Message> readHistory() throws FileNotFoundException, IOException{
        List<Message> history = new ArrayList<>();

        // Read the file
        File file = new File(FILE);
        BufferedReader reader = new BufferedReader(new FileReader(file));

        // store in history 
        String line;
        while ((line = reader.readLine()) != null && !line.startsWith(PROMPT_DIVIDER) ) {}

        // if empty return
        if (line == null) {
            reader.close();
            return history;
        }

        // read each message into history
        String thisRole = line.replace(PROMPT_DIVIDER, "").trim();
        try {
            while (reader.ready()) {
                Message message = readMessage(reader, file, false);
                String nextRole = message.role;
                message.role = thisRole;
                history.add(message);
                thisRole = nextRole;
            }
        }
        finally {
            reader.close();
        }

        Collections.reverse(history);
        return history;
    }

    public static void writeHistory(List<Message> history) throws IOException {
        File file = new File(FILE);
        try (FileWriter writer = new FileWriter(file, false)) {
        
            writer.write("\n\n");
            for (int i = history.size() - 1; i >= 0; i--) {
                Message m = history.get(i);
                writer.write(PROMPT_DIVIDER + m.role + "\n");
                String unescaped = m.content
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"");
                writer.write(unescaped);
            }

        }
    }

    public static Message readMessage(BufferedReader reader, File file, boolean triggerExit) throws IOException {
        String line;
        StringBuilder content = new StringBuilder();
        while ((line = reader.readLine()) != null && !line.startsWith(PROMPT_DIVIDER) ) {
            
            content.append(line).append("\n");

            // exit when user gives commands
            if (triggerExit && EXIT_COMMANDS.contains(line.trim().toLowerCase())) {
                reader.close();

                String existing = Files.readString(file.toPath());
                String newDiv = "\n\n" + PROMPT_DIVIDER + Role.user.name() + "\n\n";
                Files.writeString(file.toPath(), newDiv + existing);
                throw new GptExitException();
            }
        }

        String role = line != null? line.replace(PROMPT_DIVIDER, "").trim() : null;

        String contentString = content.toString()
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\\\"", "\"");

        return new Message(contentString, role);
    }



    // HELPER CLASSES


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

        
        public <T, B> T makeRequest(String url, String method, Map<String, String> headers, B body, Type responseClass) {
            
            HttpURLConnection conn = null;
            StringBuilder content = new StringBuilder();
            int statusCode = 0;
            try {
                // set up connection
                URL urlSpec = new URL(url);
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
                Class<?> listType = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
                List<Object> newList = new ArrayList<>();
                for (Map<String, Object> elem : value) {
                    if (Map.class.isAssignableFrom(listType)) {
                        newList.add(elem);
                    }
                    else {
                        Object converted = convertMapToObj(
                            getDefault(listType),
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

                if (entry.getValue() instanceof Map) {
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

            builder.deleteCharAt(builder.length() - 2);
            builder.append("}");

            return builder.toString();
        }

        private static void appendStringOrEnum(StringBuilder builder, Object value, boolean newLine) {
            String valueString = escapeQuotes(
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
                if (json.charAt(i) == '\"') {
                    int j = i+1;
                    while(json.charAt(j) != '\"') j++;
                    
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
                            if (json.charAt(j) == '{') openBrackets++;
                            if (json.charAt(j) == '}') openBrackets--;
                        }
                    }
                    else if (json.charAt(j) == '[') {
                        int openBrackets = 1;
                        while (openBrackets > 0) {
                            j++;
                            if (json.charAt(j) == '[') openBrackets++;
                            if (json.charAt(j) == ']') openBrackets--;
                        }
                    }
                    else if (json.charAt(j) == '\"') {
                        j++;
                        while (json.charAt(j) != '\"')  {
                            j++;
                            if ( json.charAt(j-1) == '\\' && json.charAt(j) == '\"') {
                                j++;
                            }
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
                // remove brackets
                valueString = valueString.substring(1, valueString.length()-1);

                // split by commas
                List<String> values = new ArrayList<>();
                int openBrackets = 0;
                int j = 0;
                for (int i = 0; i < valueString.length(); i++) {

                    if (valueString.charAt(i) == '[' || valueString.charAt(i) == '{') openBrackets++;
                    if (valueString.charAt(i) == ']' || valueString.charAt(i) == '}') openBrackets--;

                    if (valueString.charAt(i) == ',' && openBrackets == 0) {
                        values.add(valueString.substring(j, i));
                        j = i+1;
                    }
                }
                values.add(valueString.substring(j));

                // convert each value
                List<Object> list = new ArrayList<>();
                for (String val : values) {
                    list.add(getObjectFromString(val));
                }

                value = list;
            }
            else if (valueString.charAt(0) == '\"') {
                value = valueString.substring(1, valueString.length()-1);
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
                Map<T, S> newMap = new HashMap<>();
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

        /**
         * Escape quotes in strings
         */
        public static String escapeQuotes(String json) {
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