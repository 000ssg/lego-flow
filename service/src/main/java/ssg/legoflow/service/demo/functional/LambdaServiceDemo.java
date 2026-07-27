package ssg.legoflow.service.demo.functional;

import ssg.legoflow.service.Service;
import ssg.legoflow.service.functional.ServiceBuilder;

public class LambdaServiceDemo {

    @SuppressWarnings("unchecked")
    public static Service<String, Integer> createParsingService() {
        return ServiceBuilder.of(String.class, Integer.class)
                .descriptor("parser", "Parses strings to integers using lambdas")
                .onConvertToOutput((ctx, input) -> {
                    var result = new Integer[input.length];
                    for (int i = 0; i < input.length; i++) {
                        try {
                            result[i] = Integer.parseInt(input[i].trim());
                        } catch (NumberFormatException e) {
                            result[i] = 0;
                        }
                    }
                    return result;
                })
                .onConvertToInput((ctx, output) -> {
                    var result = new String[output.length];
                    for (int i = 0; i < output.length; i++) {
                        result[i] = String.valueOf(output[i]);
                    }
                    return result;
                })
                .onConnect((ctx, svc) -> ctx.getLogger().info("Parser service connected"))
                .build();
    }

    @SuppressWarnings("unchecked")
    public static Service<String, String> createUpperCaseService() {
        return ServiceBuilder.of(String.class, String.class)
                .descriptor("uppercaser", "Converts strings to uppercase")
                .onConvertToOutput((ctx, input) -> {
                    var result = new String[input.length];
                    for (int i = 0; i < input.length; i++) {
                        result[i] = input[i].toUpperCase();
                    }
                    return result;
                })
                .onConvertToInput((ctx, output) -> output)
                .build();
    }
}
