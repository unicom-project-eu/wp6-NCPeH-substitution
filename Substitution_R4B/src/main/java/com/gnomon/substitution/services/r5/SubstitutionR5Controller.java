package com.gnomon.substitution.services.r5;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import com.gnomon.substitution.services.SubstitutionServices;
import com.gnomon.substitution.utils.DoseformConversions;
import com.gnomon.substitution.utils.R5ExIngredient;
import com.gnomon.substitution.utils.helper.SubstanceEquivalence;
import org.hl7.fhir.r5.model.AdministrableProductDefinition;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Ingredient;
import org.hl7.fhir.r5.model.MedicinalProductDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;

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

        System.out.println("debug 1");

        // The list with the medicines to return
        String doseForm = _doseform;
        if (_doseform.length() == 8) {
            doseForm = DoseformConversions.getInstance().getSPOR(_doseform);
        }
        

        String substance = getParentSubstance(_substance).getBody().get_response();
        System.out.println(substance);
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

//        System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(ingredientResults));
//        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(ingredientResults);


        // 0 -> MedicinalProductDefinition
        // 1 -> ManufacturedItemDefinition
        // 2 -> AdministrableProductDefinition
        HashMap<String, String> _mpdRelations = new HashMap<>();
        HashMap<String, String> _apdRelations = new HashMap<>();
        HashMap<String, R5ExIngredient> _productList = new HashMap<>();
        for (Bundle.BundleEntryComponent entry : ingredientResults.getEntry()) {
            if (entry.getResource() instanceof Ingredient) {
                Ingredient _ingredient = (Ingredient) entry.getResource();
                R5ExIngredient _exIngredient = new R5ExIngredient(_ingredient);
                System.out.println(_ingredient.getIdPart());
                System.out.println("Reference Strength" + _ingredient.getSubstance().getStrength().get(0).getReferenceStrength().get(0).getSubstance().getConcept().getCoding().get(0).getCode());
                _ingredient.getFor().forEach(reference -> {
                    // search by string reference to a resource and add proper values to the maps
                    if (reference.getResource().getIdElement().getValue().contains("MedicinalProductDefinition")) {
                        System.out.println("Setting MPD for this ingredient");
                        _mpdRelations.put(reference.getResource().getIdElement().getIdPart(), _ingredient.getIdPart());
                    }
                    if (reference.getResource().getIdElement().getValue().contains("AdministrableProductDefinition")) {
                        System.out.println("Setting APD for this ingredient");
                        _apdRelations.put(reference.getResource().getIdElement().getIdPart(), _ingredient.getIdPart());
                    }
                });
                _productList.put(_ingredient.getIdPart(), _exIngredient);
            }

            if (entry.getResource() instanceof MedicinalProductDefinition) {
                MedicinalProductDefinition _mpd = (MedicinalProductDefinition) entry.getResource();
                System.out.println(_mpd.getIdPart());
                System.out.println(_mpd.getClassification().get(0).getCoding().get(0).getCode());
                _mpd.getClassification().forEach(codeableConcept -> {
                    codeableConcept.getCoding().forEach(coding -> {
                        if(coding.getCode().equals("C08CA01"))
                        {
                            _productList.get(_mpdRelations.get(_mpd.getIdPart())).set_mpd(_mpd);
                        }
                    });
                });
            }

            if (entry.getResource() instanceof AdministrableProductDefinition) {
                AdministrableProductDefinition _adp = (AdministrableProductDefinition) entry.getResource();
                System.out.println(_adp.getIdPart());
                _productList.get(_apdRelations.get(_adp.getIdPart())).set_apd(_adp);
            }
        }

        Bundle bundle = new Bundle();
        _productList.values().forEach(extendedIngredient -> {
            bundle.addEntry().setResource(extendedIngredient.get_mpd());
        });
        bundle.setTotal(bundle.getEntry().size());

        System.out.println(bundle.getEntry().size());
        System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

        Collections.sort(bundle.getEntry(), (o1, o2) -> {
            MedicinalProductDefinition mpd1 = (MedicinalProductDefinition) o1.getResource();
            MedicinalProductDefinition mpd2 = (MedicinalProductDefinition) o2.getResource();

            if (mpd1 != null)
                System.out.println("MPD1: " + mpd1.getIdPart());
            if (mpd2 != null)
                System.out.println("MPD2: " + mpd2.getIdPart());
            Ingredient ing1 = _productList.get(_mpdRelations.get(mpd1.getIdPart())).get_ing();
            Ingredient ing2 = _productList.get(_mpdRelations.get(mpd2.getIdPart())).get_ing();

            String substance1 = ing1.getSubstance().getCode().getConcept().getCoding().get(0).getCode();
            String substance2 = ing2.getSubstance().getCode().getConcept().getCoding().get(0).getCode();

            if (substance1.equals(_substance) && substance1.equals(substance2)) {
                return 0;
            } else
                return -1;

        });

        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }
}
