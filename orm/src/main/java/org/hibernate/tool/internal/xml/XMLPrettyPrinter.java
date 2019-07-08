/*
 * Created on 17-Dec-2004
 *
 */
package org.hibernate.tool.internal.xml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author max
 */
public final class XMLPrettyPrinter {

    public static void prettyPrintFile(File file) throws IOException {
        String input = readFile(file.getAbsolutePath(), Charset.defaultCharset());
        String output = prettyFormat(input);
        PrintWriter writer = new PrintWriter(file);
        writer.print(output);
        writer.flush();
        writer.close();
    }

    private static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    private static String prettyFormat(String input) {
        try {
            if (input != null) {
                int index = input.indexOf(0x0);
                if (index > 0) {
                    System.out.println(input);
                    StringBuilder sb = new StringBuilder(input.length());
                    for (int i = 0; i < input.length(); i++) {
                        char c = input.charAt(i);
                        if (c != 0x0) {
                            sb.append(c);
                        }
                    }
                    input = sb.toString();
                }
            }
            return XMLPrettyPrinterStrategyFactory.newXMLPrettyPrinterStrategy().prettyPrint(input);
        } catch (Exception e) {
            e.printStackTrace();
            return input;
        }
    }

}
