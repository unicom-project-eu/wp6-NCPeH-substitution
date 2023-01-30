package com.gnomon.substitution.services;

import com.gnomon.substitution.utils.helper.SubstanceEquivalence;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.command.KieCommands;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.ObjectFilter;
import org.kie.internal.command.CommandFactory;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class SubstitutionServices {

        @Value("${kie.containerId}")
        private String containerId;
        //@Autowired
        private String outIdentifier = "response";

        //private KieContainer kieContainer;

        //private static final String SALE_IDENTIFIER = "Sale";

        //private static final Logger LOGGER = LoggerFactory.getLogger(OrderDiscountService.class);

        @Autowired
        private KieServicesClient kieServicesClient;

        public SubstanceEquivalence discountCalculator (SubstanceEquivalence incomeObj) {
            SubstanceEquivalence response = null;

            // TODO: Need to find a better way to use the connection with the KIE server
            kieServicesClient.activateContainer(containerId);
            RuleServicesClient client = kieServicesClient.getServicesClient(RuleServicesClient.class);

//            List<SubstanceEquivalence> facts = new ArrayList<>();
//            facts.add(incomeObj);

            //BatchExecutionCommand batchExecutionCommand = batchCommand(incomeObj);
            Command<?> batchCommand = prepareCommands(incomeObj, "gnomon", outIdentifier);

            ServiceResponse<ExecutionResults> result = client.executeCommandsWithResults(containerId, batchCommand);

            if (result.getType() == ServiceResponse.ResponseType.SUCCESS) {
                ArrayList<SubstanceEquivalence> results = (ArrayList)result.getResult().getValue(outIdentifier);
                log.info("Commands executed with success! Response: ");
                log.info(result.getMsg());
                log.info("{}", result.getResult());
                System.out.println(results.size());
                response = (SubstanceEquivalence) results.get(0);
            } else {
                System.out.println("Something went wrong!!");
            }

            kieServicesClient.deactivateContainer(containerId);
            return response;
        }

        private BatchExecutionCommand batchCommand(SubstanceEquivalence incomeObj) {
            List<Command<?>> cmds = buildCommands(incomeObj);
            return CommandFactory.newBatchExecution(cmds);
        }

        private List<Command<?>> buildCommands(SubstanceEquivalence incomeObj) {
            List<Command<?>> cmds = new ArrayList<>();
            KieCommands commands = KieServices.Factory.get().getCommands();
            cmds.add(commands.newInsert(incomeObj, outIdentifier));
            //cmds.add(commands.newGetObjects(outIdentifier));
            cmds.add(commands.newFireAllRules());
            cmds.add(commands.newGetObjects(outIdentifier));
            return cmds;
        }

        private Command<?> prepareCommands(SubstanceEquivalence customerRequest, String sessionName, String outIdentifier) {
            List<Command<?>> cmds = new ArrayList<>();
            KieCommands commands = KieServices.Factory.get().getCommands();
            cmds.add(commands.newInsert(customerRequest));
            cmds.add(commands.newFireAllRules("firedActivations"));
            ObjectFilter factsFilter = new ClassObjectFilter(SubstanceEquivalence.class);
            cmds.add(commands.newGetObjects(factsFilter,outIdentifier));
            return commands.newBatchExecution(cmds);
        }

    }

