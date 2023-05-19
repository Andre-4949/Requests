package de.andre.Requests;

public class Main {

    public static void main(String[] args){
        System.out.println(new Request("https://raw.githubusercontent.com/kodecocodes/recipes/master/Recipes.json", RequestMode.GET).setUseRandomUserAgent(true).makeRequest());
        System.out.println(Request.getLocalIP());
    }

}
