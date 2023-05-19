package de.andre.Requests;


import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum RegexStringDataBase {
    LINK_VALIDATION("(http(s?):\\/\\/)?(\\.|[a-zA-Z]|-)*\\.[a-zA-Z]{2,}(\\/\\S+)*"),
    PARSE_IP("((\\d{1,3})\\.){3}\\d{1,3}$");

    public String regex = "";

    RegexStringDataBase(String regex) {
        this.regex = regex;
    }

    @NotNull
    public ArrayList<String> parseFromString(String s) {
        Pattern pattern = Pattern.compile(this.regex);
        Matcher matcher = pattern.matcher(s);
        ArrayList<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }
}
