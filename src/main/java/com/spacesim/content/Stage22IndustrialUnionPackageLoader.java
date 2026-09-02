package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.MissionTemplateDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.RecurringNpcDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.StationVariantDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.StoryChainDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.VisualRuleDefinition;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict data loader for the M22.4 Industrial Union authored package. */
public final class Stage22IndustrialUnionPackageLoader {
    public static final String DEFAULT_RESOURCE = "data/content/stage22-industrial-union-package-v1.json";
    private static final int MAX_ITEMS = 128;
    private Stage22IndustrialUnionPackageLoader() { throw new AssertionError("utility class"); }

    public static Stage22IndustrialUnionPackageCatalog loadDefault() {
        try (InputStream stream = Stage22IndustrialUnionPackageLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing Industrial Union package resource: " + DEFAULT_RESOURCE);
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Industrial Union package resource", exception);
        }
    }

    public static Stage22IndustrialUnionPackageCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) throw new IllegalArgumentException("Industrial Union package JSON must not be blank");
        final JsonValue root;
        try { root = new JsonReader().parse(json); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Malformed Industrial Union package JSON", exception); }
        object(root, "root");
        int schema = integer(root, "schemaVersion");
        if (schema != 1) throw new IllegalArgumentException("Unsupported Industrial Union package schema: " + schema);
        return new Stage22IndustrialUnionPackageCatalog(schema, string(root,"catalogVersion"), string(root,"packageKey"),
                string(root,"stableFactionId"), ships(root), stations(root), npcs(root), missions(root), chains(root), visuals(root));
    }

    private static List<ShipFamilyDefinition> ships(JsonValue root) {
        List<ShipFamilyDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"shipFamilies").child;n!=null;n=n.next){ object(n,"ship family"); result.add(new ShipFamilyDefinition(
                string(n,"familyId"),string(n,"roleId"),string(n,"primaryFitId"),string(n,"refitFitId"),
                string(n,"productionManifestId"),string(n,"visualBindingId"),string(n,"lineageId"),string(n,"fleetUse"),string(n,"counterplay"))); }
        return result;
    }
    private static List<StationVariantDefinition> stations(JsonValue root) {
        List<StationVariantDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"stations").child;n!=null;n=n.next){ object(n,"station"); result.add(new StationVariantDefinition(
                string(n,"id"),string(n,"stage18ArchetypeId"),stringList(n,"requiredFacilityIds"),string(n,"visualBrief"))); }
        return result;
    }
    private static List<RecurringNpcDefinition> npcs(JsonValue root) {
        List<RecurringNpcDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"recurringNpcs").child;n!=null;n=n.next){ object(n,"npc"); result.add(new RecurringNpcDefinition(
                string(n,"id"),string(n,"nameKey"),enumValue(n,"role",NpcRole.class),string(n,"characterOverlayId"),string(n,"publicVoice"),string(n,"privateVoice"))); }
        return result;
    }
    private static List<MissionTemplateDefinition> missions(JsonValue root) {
        List<MissionTemplateDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"missions").child;n!=null;n=n.next){ object(n,"mission"); result.add(new MissionTemplateDefinition(
                string(n,"id"),string(n,"issuerNpcId"),enumValue(n,"runtimeTemplate",MissionTemplate.class),
                enumValue(n,"authority",ObjectiveAuthority.class),enumValue(n,"objectiveKind",ObjectiveKind.class),string(n,"semanticIntent"))); }
        return result;
    }
    private static List<StoryChainDefinition> chains(JsonValue root) {
        List<StoryChainDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"storyChains").child;n!=null;n=n.next){ object(n,"story chain"); result.add(new StoryChainDefinition(
                string(n,"id"),stringList(n,"missionTemplateIds"),string(n,"semanticIntent"))); }
        return result;
    }
    private static List<VisualRuleDefinition> visuals(JsonValue root) {
        List<VisualRuleDefinition> result=new ArrayList<>();
        for(JsonValue n=array(root,"visualRules").child;n!=null;n=n.next){ object(n,"visual rule"); result.add(new VisualRuleDefinition(
                string(n,"id"),string(n,"medium"),string(n,"authorityDocument"),string(n,"requirement"))); }
        return result;
    }
    private static JsonValue array(JsonValue parent,String name){ JsonValue v=parent.get(name); if(v==null||!v.isArray())throw new IllegalArgumentException(name+" must be an array"); if(v.size>MAX_ITEMS)throw new IllegalArgumentException(name+" exceeds maximum size"); return v; }
    private static List<String> stringList(JsonValue p,String name){ List<String> r=new ArrayList<>(); for(JsonValue n=array(p,name).child;n!=null;n=n.next){ if(!n.isString()||n.asString().isBlank())throw new IllegalArgumentException(name+" must contain text"); r.add(n.asString()); } return r; }
    private static String string(JsonValue n,String name){ JsonValue v=n.get(name); if(v==null||!v.isString()||v.asString().isBlank())throw new IllegalArgumentException(name+" must be text"); return v.asString(); }
    private static int integer(JsonValue n,String name){ JsonValue v=n.get(name); if(v==null||!v.isNumber())throw new IllegalArgumentException(name+" must be integer"); return v.asInt(); }
    private static <E extends Enum<E>> E enumValue(JsonValue n,String name,Class<E> type){ String v=string(n,name); try{return Enum.valueOf(type,v);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Unsupported "+name+": "+v,e);} }
    private static void object(JsonValue v,String label){ if(v==null||!v.isObject())throw new IllegalArgumentException(label+" must be an object"); }
}
