package com.gnomon.substitution.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;

/**
 *
 */
public class DoseformConversions {

    private HashMap<String, String> _sporToEDQM;
    private HashMap<String, String> _edqmToSpor;
    private static DoseformConversions instance = null;

    private DoseformConversions() {
        // private constructor
        _sporToEDQM = new HashMap<>();
        _edqmToSpor = new HashMap<>();
    }

    public static DoseformConversions getInstance() {
        if (instance == null) {
            instance = new DoseformConversions();
        }
        return instance;
    }

    public String getSPOR(String _EDQM) {
        return _edqmToSpor.get(_EDQM);
    }

    public String getEDQM(String _SPOR) {
        return _sporToEDQM.get(_SPOR);
    }

    public void load() {// read the JSON file
        // read the JSON file
        FileReader reader = null;
        try {
            reader = new FileReader("data/SPOR_EDQM.json");

            Gson gson = new Gson();
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonTree = jsonParser.parse(reader);

            // check if JsonElement is a jsonArray
            if (jsonTree.isJsonArray()) {
                JsonArray jsonArray = jsonTree.getAsJsonArray();
                for (JsonElement element : jsonArray) {

                    // do something with each element
                    // you can use gson.fromJson() method to convert json element to your pojo class
//                    Doseforms obj = gson.fromJson(element, Doseforms.class);
                    _edqmToSpor.put(element.getAsJsonObject().get("EDQM").getAsString(), element.getAsJsonObject().get("RMS").getAsString());
                    _sporToEDQM.put(element.getAsJsonObject().get("RMS").getAsString(), element.getAsJsonObject().get("EDQM").getAsString());
                }
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


    }

}
