package com.gnomon.substitution.utils;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r5.model.AdministrableProductDefinition;
import org.hl7.fhir.r5.model.Ingredient;
import org.hl7.fhir.r5.model.MedicinalProductDefinition;

@Getter
@Setter
public class R5ExIngredient {

    private MedicinalProductDefinition _mpd;
    private AdministrableProductDefinition _apd;

    private Ingredient _ing;

    public R5ExIngredient(Ingredient ing) {
        _ing = ing;
    }
}
