package com.gnomon.substitution.services.r4b;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.gnomon.substitution.services.SubstitutionServices;
import com.gnomon.substitution.utils.DoseformConversions;
import com.gnomon.substitution.utils.ExtendedIngredient;
import com.gnomon.substitution.utils.helper.SubstanceEquivalence;
import org.hl7.fhir.r4b.model.AdministrableProductDefinition;
import org.hl7.fhir.r4b.model.Bundle;
import org.hl7.fhir.r4b.model.Ingredient;
import org.hl7.fhir.r4b.model.MedicinalProductDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;

@RestController
public class SubstitutionR4BController {

    @Autowired
    private SubstitutionServices discountService;

    @GetMapping("/r4b/getEquivalentSubstance")
    public ResponseEntity<SubstanceEquivalence> getParentSubstance(@RequestParam(required = true, name = "substance") String substance) {
        SubstanceEquivalence incomeObj = new SubstanceEquivalence();
        incomeObj.set_substance(substance);
        System.out.println(substance);
        System.out.println(incomeObj.get_substance());
        return new ResponseEntity<SubstanceEquivalence>(discountService.discountCalculator(incomeObj), HttpStatus.OK);
    }

    @RequestMapping(value = "/r4b/substitutes", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public String getEquivalent3(@RequestParam(name = "substance") String _substance, @RequestParam(name = "doseform") String _doseform, @RequestParam(required = false, name = "country") String targetCountry,
                                 @RequestParam(required = false, name = "strength") String _strength,
                                 @RequestParam(required = false, name = "productname") String _productname) {

        String country = targetCountry == null || targetCountry.isEmpty() ? "100000072172" : targetCountry; // if targetCountry is not set, then force Estonia
        // Create a context
        FhirContext ctx = FhirContext.forR4B();
        // Disable server validation (don't pull the server's metadata first)
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        // Now create a client and use it
        IGenericClient client = ctx.newRestfulGenericClient("https://jpar4b.unicom.datawizard.it/fhir");

        // The list with the medicines to return
        String doseForm = _doseform;
        if (_doseform.length() == 8) {
            doseForm = DoseformConversions.getInstance().getSPOR(_doseform);
        }

        // Specific Substance has zero results ex. no results for Amlodipine Besilate
        // So we need to check one level higher by ATC (or SPOR equivalent code)
        // get parent of requested substance
        String substance = getParentSubstance(_substance).getBody().get_response();
        Bundle ingredientResults = client
                .search()
                .forResource(Ingredient.class)
                .where(Ingredient.FOR.hasChainedProperty(MedicinalProductDefinition.PRODUCT_CLASSIFICATION.exactly().code(substance)))
                .and(Ingredient.FOR.hasChainedProperty(MedicinalProductDefinition.NAME_LANGUAGE.exactly().code(country)))
                .include(Ingredient.INCLUDE_FOR)
                .returnBundle(Bundle.class)
                .execute();

        // 0 -> MedicinalProductDefinition
        // 1 -> ManufacturedItemDefinition
        // 2 -> AdministrableProductDefinition
        HashMap<String, String> _mpdRelations = new HashMap<>();
        HashMap<String, String> _apdRelations = new HashMap<>();
        HashMap<String, ExtendedIngredient> _productList = new HashMap<>();
        for (Bundle.BundleEntryComponent entry : ingredientResults.getEntry()) {
            if (entry.getResource() instanceof Ingredient) {
                Ingredient _ingredient = (Ingredient) entry.getResource();
                ExtendedIngredient _exIngredient = new ExtendedIngredient(_ingredient);
//                _exIngredient.set_ing(_ingredient);
                System.out.println(_ingredient.getIdPart());
                System.out.println("Reference Strength" + _ingredient.getSubstance().getStrength().get(0).getReferenceStrength().get(0).getSubstance().getConcept().getCoding().get(0).getCode());
                System.out.println(_ingredient.getFor().get(0).getResource().getIdElement().getIdPart());
                _mpdRelations.put(_ingredient.getFor().get(0).getResource().getIdElement().getIdPart(), _ingredient.getIdPart());
                _apdRelations.put(_ingredient.getFor().get(2).getResource().getIdElement().getIdPart(), _ingredient.getIdPart());
                _productList.put(_ingredient.getIdPart(), _exIngredient);
            }

            if (entry.getResource() instanceof MedicinalProductDefinition) {
                MedicinalProductDefinition _mpd = (MedicinalProductDefinition) entry.getResource();
                System.out.println(_mpd.getIdPart());
                _productList.get(_mpdRelations.get(_mpd.getIdPart())).set_mpd(_mpd);
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

        Collections.sort(bundle.getEntry(), (o1, o2) -> {
            MedicinalProductDefinition mpd1 = (MedicinalProductDefinition) o1.getResource();
            MedicinalProductDefinition mpd2 = (MedicinalProductDefinition) o2.getResource();

            Ingredient ing1 = _productList.get(_mpdRelations.get(mpd1.getIdPart())).get_ing();
            Ingredient ing2 = _productList.get(_mpdRelations.get(mpd2.getIdPart())).get_ing();

            String substance1 = ing1.getSubstance().getCode().getConcept().getCoding().get(0).getCode();
            String substance2 = ing2.getSubstance().getCode().getConcept().getCoding().get(0).getCode();

            if (substance1.equals(_substance) && substance1.equals(substance2)) {
                return 1;
            } else
                return -1;

        });

        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }
}
