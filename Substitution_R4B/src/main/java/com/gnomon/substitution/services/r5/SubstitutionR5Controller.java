package com.gnomon.substitution.services.r5;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import com.gnomon.substitution.services.SubstitutionServices;
import com.gnomon.substitution.utils.DoseformConversions;
import com.gnomon.substitution.utils.helper.SubstanceEquivalence;
import org.hl7.fhir.r4b.model.MedicinalProductDefinition;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Ingredient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubstitutionR5Controller {

    @Autowired
    private SubstitutionServices discountService;

    @GetMapping("/r5/getEquivalentSubstance")
    public ResponseEntity<SubstanceEquivalence> getParentSubstance(@RequestParam(required = true, name = "substance") String substance) {
        SubstanceEquivalence incomeObj = new SubstanceEquivalence();
        incomeObj.set_substance(substance);
        System.out.println(substance);
        System.out.println(incomeObj.get_substance());
        return new ResponseEntity<SubstanceEquivalence>(discountService.discountCalculator(incomeObj), HttpStatus.OK);
    }


    @GetMapping("/r5/substitutes")
    public String substitute(@RequestParam(name = "substance") String _substance, @RequestParam(name = "doseform") String _doseform, @RequestParam(required = false, name = "country") String targetCountry,
                             @RequestParam(required = false, name = "strength") String _strength,
                             @RequestParam(required = false, name = "productname") String _productname) {

        FhirContext ctx = FhirContext.forR5();
        // Disable server validation (don't pull the server's metadata first)
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        // Now create a client and use it
        IGenericClient client = ctx.newRestfulGenericClient("https://jpa.unicom.datawizard.it/fhir");

        String country = targetCountry == null || targetCountry.isEmpty() ? "100000072172" : targetCountry; // if targetCountry is not set, then force Estonia

        // The list with the medicines to return
        String doseForm = _doseform;
        if (_doseform.length() == 8) {
            doseForm = DoseformConversions.getInstance().getSPOR(_doseform);
        }

        String substance = getParentSubstance(_substance).getBody().get_response();

        // Specific Substance has zero results ex. no results for Amlodipine Besilate
        // So we need to check one level higher by ATC (or SPOR equivalent code)
        // get parent of requested substance
        Bundle ingredientResults = client
                .search()
                .forResource(Ingredient.class)
                .where(new TokenClientParam("reference-strength-substance").exactly().code(substance))
//                .where(Ingredient.FOR.hasChainedProperty(MedicinalProductDefinition.PRODUCT_CLASSIFICATION.exactly().code(substance)))
                .and(Ingredient.FOR.hasChainedProperty(MedicinalProductDefinition.NAME_LANGUAGE.exactly().code(country)))
                .include(Ingredient.INCLUDE_FOR)
                .returnBundle(Bundle.class)
                .execute();

        System.out.println(ingredientResults.getEntry().size());
        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(ingredientResults);
    }
}
