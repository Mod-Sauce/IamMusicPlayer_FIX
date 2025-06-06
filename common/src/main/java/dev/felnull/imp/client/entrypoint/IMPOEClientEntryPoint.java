package dev.felnull.imp.client.entrypoint;

import dev.felnull.imp.client.model.IMPModels;
import org.modsauce.otyacraftenginerenewed.client.callpoint.ClientCallPoint;
import org.modsauce.otyacraftenginerenewed.client.callpoint.ModelRegister;

@ClientCallPoint.Sign
public class IMPOEClientEntryPoint implements ClientCallPoint {
    @Override
    public void onModelRegistry(ModelRegister register) {
        IMPModels.init(register);
    }
}
