package fr.openent.zimbra.service;

import fr.openent.zimbra.model.synchro.Structure;
import io.vertx.core.Future;

import java.util.List;

public interface StructureService {
    public Future<List<Structure>> getStructuresAndAdmls(List<String> structuresId);
}
