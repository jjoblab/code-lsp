package com.example.monip;

public class Greeter {

    private String politesse = "Bonjour";

    public String saluer(String nom) {
        return politesse + " " + nom + " !";
    }

    public String repeter(String nom, int fois) {
        return nom.repeat(fois);
    }

    public String getPolitesse() {
        return politesse;
    }

    public void setPolitesse(String politesse) {
        this.politesse = politesse;
    }
}
