package com.gnomon.substitution.utils;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4b.model.AdministrableProductDefinition;
import org.hl7.fhir.r4b.model.Ingredient;
import org.hl7.fhir.r4b.model.MedicinalProductDefinition;

@Getter
@Setter
public class ExtendedIngredient {

    private MedicinalProductDefinition _mpd;
    private AdministrableProductDefinition _apd;

    private Ingredient _ing;

    public ExtendedIngredient(Ingredient ing) {
        _ing = ing;
    }
}
