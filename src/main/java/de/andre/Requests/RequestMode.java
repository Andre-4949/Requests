package de.andre.Requests;

public enum RequestMode {
    GET("GET"),
    POST("POST"),
    DELETE("DELETE"),
    PUT("PUT");

    private final String mode;
    RequestMode(String mode){
        this.mode = mode;
    }

    public String mode(){
        return this.mode;
    }
}
