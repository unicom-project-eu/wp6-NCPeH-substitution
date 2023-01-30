package com.gnomon.substitution;

import com.gnomon.substitution.utils.DoseformConversions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        DoseformConversions.getInstance().load();
        SpringApplication.run(Main.class, args);
//        System.out.println(controller.getMedicationKnowledge("100000085259","10219000").size());

    }
}