package com.gnomon.substitution.services;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.gnomon.substitution.utils.ExtendedIngredient;
import com.gnomon.substitution.utils.helper.SubstanceEquivalence;
import com.gnomon.substitution.utils.DoseformConversions;
import org.hl7.fhir.r4b.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
public class SubstitutionController {

    @Autowired
    private SubstitutionServices discountService;

    @GetMapping("/getEquivalentSubstance")
    public ResponseEntity<SubstanceEquivalence> getParentSubstance(@RequestParam(required = true, name = "substance") String substance) {
        SubstanceEquivalence incomeObj = new SubstanceEquivalence();
        incomeObj.set_substance(substance);
        System.out.println(substance);
        System.out.println(incomeObj.get_substance());
        return new ResponseEntity<SubstanceEquivalence>(discountService.discountCalculator(incomeObj), HttpStatus.OK);
    }

    @GetMapping("/equivalentMedicationList")
    public String getEquivalent(@RequestParam(required = true, name = "substance") String substance, @RequestParam(required = true, name = "doseform") String doseform, @RequestParam(required = false, name = "country") String targetCountry) {
        String country = targetCountry == null || targetCountry.isEmpty() ? "100000072172" : targetCountry; // if targetCountry is not set, then force Estonia
        // Create a context
        FhirContext ctx = FhirContext.forR4B();
        // Disable server validation (don't pull the server's metadata first)
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        // Now create a client and use it
        IGenericClient client = ctx.newRestfulGenericClient("https://jpa.unicom.datawizard.it/fhir");

        List<MedicinalProductDefinition> mpdList = new ArrayList<>();

        // Invoke the client
        Bundle ingredientResults = client
                .search()
                .forResource(Ingredient.class)
                .where(Ingredient.SUBSTANCE_CODE.exactly().code(substance))
                .returnBundle(Bundle.class)
                .execute();

        if (ingredientResults.getEntry().size() != 0) {
            for (Bundle.BundleEntryComponent entry : ingredientResults.getEntry()) {
                Ingredient ingredient = (Ingredient) entry.getResource();

                // This should be second layer of filtering by Reference Substance (Just Amlodipine no modifier)
                System.out.println(ingredient.getSubstance().getStrength().get(0).getReferenceStrength().get(0).getSubstance().getConcept().getCoding().get(0).getCode());
                // ingredient.getFor().get(x) as for in Ingredient has:
                // 0 -> MedicinalProductDefinition
                // 1 -> ManufacturedItemDefinition
                // 2 -> AdministrableProductDefinition

                // This code should be removed once the URL of the FHIR server is by country
                MedicinalProductDefinition mpd = client.read().resource(MedicinalProductDefinition.class).withUrl(ingredient.getFor().get(0).getReference())
                        .execute();
                CodeableConcept languages = mpd.getName().get(0).getCountryLanguage().get(0).getLanguage();
                if (languages != null && !languages.isEmpty()) {
                    String code = languages.getCoding().get(0).getCode();
//                String display = languages.getCoding().get(0).getDisplay();
//                System.out.println("Language: " + code + " " + display);

                    // Limit data by country
                    if (code.equals(country)) {

                        AdministrableProductDefinition adp = client.read().resource(AdministrableProductDefinition.class).withUrl(ingredient.getFor().get(2).getReference()).execute();
                        if (adp.getAdministrableDoseForm().getCoding().get(0).getCode().equals(doseform)) {
                            mpdList.add(mpd);
                        }
                    }
                }
            }
        } else {
            // Specific Substance has zero results ex. no results for Amlodipine Besilate
            // So we need to check one level higher by ATC (or SPOR equivalent code)

            // get parent of requested substance
            substance = getParentSubstance(substance).getBody().get_response();//substance.equals("100000090079") ? "C08CA01" : "100000095065"; // force amlodipine either ATC or SPOR
            Bundle mpdResults = client
                    .search()
                    .forResource(MedicinalProductDefinition.class)
                    .where(MedicinalProductDefinition.PRODUCT_CLASSIFICATION.exactly().code(substance))
                    .and(MedicinalProductDefinition.NAME_LANGUAGE.exactly().code(country))
                    .returnBundle(Bundle.class)
                    .execute();

            System.out.println(mpdResults.getEntry().size());
            HashMap<String, MedicinalProductDefinition> _mpds = new HashMap<>();
            mpdResults.getEntry().forEach(bundleEntryComponent -> {
                MedicinalProductDefinition mpd = (MedicinalProductDefinition) bundleEntryComponent.getResource();
                System.out.println(mpd.getIdElement().getIdPart());
                _mpds.put(mpd.getIdElement().getIdPart(), mpd);
            });

            Bundle adp = client.search().forResource(AdministrableProductDefinition.class)
                    .where(AdministrableProductDefinition.FORM_OF.hasAnyOfIds(_mpds.keySet()))
                    .and(AdministrableProductDefinition.DOSE_FORM.exactly().code(doseform))
                    .returnBundle(Bundle.class)
                    .execute();
            System.out.println(adp.getEntry().size());
            adp.getEntry().forEach(bundleEntryComponent -> {
                AdministrableProductDefinition apd = (AdministrableProductDefinition) bundleEntryComponent.getResource();
                System.out.println(apd.getFormOf().get(0));
//                if (apd.getFormOf().get(0)) {
//                    mpdList.add(_mpds.get());
//                }
            });
//            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(adp));
        }


        System.out.println(mpdList.size());

        Bundle bundle = new Bundle();
        mpdList.forEach(medicinalProductDefinition -> {
            bundle.addEntry().setResource(medicinalProductDefinition);
        });
        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    @GetMapping("/equivalentMedicationListID")
    public List<String> getEquivalentId(@RequestParam(required = true, name = "substance") String substance, @RequestParam(required = true, name = "doseform") String doseform, @RequestParam(required = false, name = "country") String targetCountry) {
        String country = targetCountry == null || targetCountry.isEmpty() ? "100000072172" : targetCountry; // if targetCountry is not set, then force Estonia
        // Create a context
        FhirContext ctx = FhirContext.forR4B();
        // Disable server validation (don't pull the server's metadata first)
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        // Now create a client and use it
        IGenericClient client = ctx.newRestfulGenericClient("https://jpa.unicom.datawizard.it/fhir");

        List<String> mpdList = new ArrayList<>();

        // Invoke the client
        Bundle ingredientResults = client
                .search()
                .forResource(Ingredient.class)
                .where(Ingredient.SUBSTANCE_CODE.exactly().code(substance))
                .returnBundle(Bundle.class)
                .execute();

        if (ingredientResults.getEntry().size() != 0) {
            for (Bundle.BundleEntryComponent entry : ingredientResults.getEntry()) {
                Ingredient ingredient = (Ingredient) entry.getResource();

                // This should be second layer of filtering by Reference Substance (Just Amlodipine no modifier)
                System.out.println(ingredient.getSubstance().getStrength().get(0).getReferenceStrength().get(0).getSubstance().getConcept().getCoding().get(0).getCode());
                // ingredient.getFor().get(x) as for in Ingredient has:
                // 0 -> MedicinalProductDefinition
                // 1 -> ManufacturedItemDefinition
                // 2 -> AdministrableProductDefinition

                // This code should be removed once the URL of the FHIR server is by country
                MedicinalProductDefinition mpd = client.read().resource(MedicinalProductDefinition.class).withUrl(ingredient.getFor().get(0).getReference())
                        .execute();
                CodeableConcept languages = mpd.getName().get(0).getCountryLanguage().get(0).getLanguage();
                if (languages != null && !languages.isEmpty()) {
                    String code = languages.getCoding().get(0).getCode();
//                String display = languages.getCoding().get(0).getDisplay();
//                System.out.println("Language: " + code + " " + display);

                    // Limit data by country
                    if (code.equals(country)) {

                        AdministrableProductDefinition adp = client.read().resource(AdministrableProductDefinition.class).withUrl(ingredient.getFor().get(2).getReference()).execute();
                        if (adp.getAdministrableDoseForm().getCoding().get(0).getCode().equals(doseform)) {
                            mpdList.add(mpd.getIdElement().getIdPart());
                        }
                    }
                }
            }
        } else {
            // Specific Substance has zero results ex. no results for Amlodipine Besilate
            // So we need to check one level higher by ATC (or SPOR equivalent code)

            // get parent of requested substance
            substance = getParentSubstance(substance).getBody().get_response();//substance.equals("100000090079") ? "C08CA01" : "100000095065"; // force amlodipine either ATC or SPOR
            Bundle mpdResults = client
                    .search()
                    .forResource(MedicinalProductDefinition.class)
                    .where(MedicinalProductDefinition.PRODUCT_CLASSIFICATION.exactly().code(substance))
                    .and(MedicinalProductDefinition.NAME_LANGUAGE.exactly().code(country))
                    .returnBundle(Bundle.class)
                    .execute();

            System.out.println(mpdResults.getEntry().size());
            HashMap<String, MedicinalProductDefinition> _mpds = new HashMap<>();
            mpdResults.getEntry().forEach(bundleEntryComponent -> {
                MedicinalProductDefinition mpd = (MedicinalProductDefinition) bundleEntryComponent.getResource();
                System.out.println(mpd.getIdElement().getIdPart());
                _mpds.put(mpd.getIdElement().getIdPart(), mpd);
                mpdList.add(mpd.getIdElement().getIdPart());
            });

            Bundle adp = client.search().forResource(AdministrableProductDefinition.class)
                    .where(AdministrableProductDefinition.FORM_OF.hasAnyOfIds(_mpds.keySet()))
                    .and(AdministrableProductDefinition.DOSE_FORM.exactly().code(doseform))
                    .returnBundle(Bundle.class)
                    .execute();
            System.out.println(adp.getEntry().size());
            adp.getEntry().forEach(bundleEntryComponent -> {
                AdministrableProductDefinition apd = (AdministrableProductDefinition) bundleEntryComponent.getResource();
                System.out.println(apd.getFormOf().get(0));
//                if (apd.getFormOf().get(0)) {
//                    mpdList.add(_mpds.get());
//                }
            });
//            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(adp));
        }


        System.out.println(mpdList.size());

        return mpdList;
    }


    @RequestMapping(value = "/medicinalproducts", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public String getEquivalent2(@RequestParam(name = "substance") String _substance, @RequestParam(name = "doseform") String _doseform, @RequestParam(required = false, name = "country") String targetCountry,
                                 @RequestParam(required = false, name = "strength") String _strength,
                                 @RequestParam(required = false, name = "productname") String _productname) {
        String country = targetCountry == null || targetCountry.isEmpty() ? "100000072172" : targetCountry; // if targetCountry is not set, then force Estonia
        // Create a context
        FhirContext ctx = FhirContext.forR4B();
        // Disable server validation (don't pull the server's metadata first)
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        // Now create a client and use it
        IGenericClient client = ctx.newRestfulGenericClient("https://jpa.unicom.datawizard.it/fhir");

        // The list with the medicines to return
        HashMap<String, MedicinalProductDefinition> mpdList = new HashMap<>();
        String doseForm = _doseform;
        if (_doseform.length() == 8) {
            doseForm = DoseformConversions.getInstance().getSPOR(_doseform);
        }

        // Specific Substance has zero results ex. no results for Amlodipine Besilate
        // So we need to check one level higher by ATC (or SPOR equivalent code)
        // get parent of requested substance
        String substance = getParentSubstance(_substance).getBody().get_response();
        // Get all MPDs where Substance is the parent of the requested substance for the target country
        Bundle mpdResults = client
                .search()
                .forResource(MedicinalProductDefinition.class)
                .where(MedicinalProductDefinition.PRODUCT_CLASSIFICATION.exactly().code(substance))
                .and(MedicinalProductDefinition.NAME_LANGUAGE.exactly().code(country))
                .returnBundle(Bundle.class)
                .execute();

        // TODO: remove debug
        System.out.println(mpdResults.getEntry().size());

        // Save a Map of mpd ID and mpd
        HashMap<String, MedicinalProductDefinition> _mpds = new HashMap<>();
        mpdResults.getEntry().forEach(bundleEntryComponent -> {
            MedicinalProductDefinition mpd = (MedicinalProductDefinition) bundleEntryComponent.getResource();
            _mpds.put(mpd.getIdElement().getIdPart(), mpd);
        });

        // Check if for the given MPDs and the dose form there are any results.
        Bundle adp = client.search().forResource(AdministrableProductDefinition.class)
                .where(AdministrableProductDefinition.FORM_OF.hasAnyOfIds(_mpds.keySet()))
                .and(AdministrableProductDefinition.DOSE_FORM.exactly().code(doseForm))
                .returnBundle(Bundle.class)
                .execute();

        // TODO: remove debug
        System.out.println(adp.getEntry().size());
        if (adp.getEntry().size() == 0) {
            System.out.println("Debug 1: " + _doseform);
            doseForm = getParentSubstance(_doseform).getBody().get_response();
            System.out.println("Debug 1: " + doseForm);
        }

        adp.getEntry().forEach(bundleEntryComponent -> {
            AdministrableProductDefinition _apd = (AdministrableProductDefinition) bundleEntryComponent.getResource();

//            System.out.println(_apd.getAdministrableDoseForm().getCoding().get(0).getCode().equals(_doseform));

            if (!mpdList.containsKey(_apd.getFormOf().get(0).getReferenceElement().getIdPart())) {
                mpdList.put(_apd.getFormOf().get(0).getReferenceElement().getIdPart(), _mpds.get(_apd.getFormOf().get(0).getReferenceElement().getIdPart()));
            }
        });


        Bundle bundle = new Bundle();
        mpdList.values().forEach(medicinalProductDefinition -> {
            bundle.addEntry().setResource(medicinalProductDefinition);
        });

        Collections.sort(bundle.getEntry(), (o1, o2) -> {
            MedicinalProductDefinition mpd1 = (MedicinalProductDefinition) o1.getResource();
            MedicinalProductDefinition mpd2 = (MedicinalProductDefinition) o2.getResource();

            Ingredient ing1 = (Ingredient) client.search().forResource(Ingredient.class).where(Ingredient.FOR.hasId(mpd1.getIdPart()))
                    .returnBundle(Bundle.class)
                    .execute().getEntry().get(0).getResource();

            Ingredient ing2 = (Ingredient) client.search().forResource(Ingredient.class).where(Ingredient.FOR.hasId(mpd2.getIdPart()))
                    .returnBundle(Bundle.class)
                    .execute().getEntry().get(0).getResource();

            String substance1 = ing1.getSubstance().getCode().getConcept().getCoding().get(0).getCode();
            String substance2 = ing2.getSubstance().getCode().getConcept().getCoding().get(0).getCode();

            if (substance1.equals(_substance) && substance1.equals(substance2)) {
                return 1;
            } else
                return -1;

        });
        bundle.setTotal(bundle.getEntry().size());

        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }

    @RequestMapping(value = "/medicinalproducts2", method = RequestMethod.GET, produces = "application/json")
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
        IGenericClient client = ctx.newRestfulGenericClient("https://jpa.unicom.datawizard.it/fhir");

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