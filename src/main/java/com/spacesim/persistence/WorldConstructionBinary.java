package com.spacesim.persistence;

import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.StarSystemId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class WorldConstructionBinary {
    private static final int MAX_PROJECTS = 100_000;
    private static final int MAX_MATERIALS_PER_PROJECT = 10_000;

    private WorldConstructionBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<ConstructionProjectState> projects)
            throws IOException {
        WorldIoSupport.writeCount(out, projects.size(), MAX_PROJECTS, "constructionProjects");
        for (ConstructionProjectState project : projects) {
            out.writeLong(project.id().value());
            WorldIoSupport.writeString(out, project.ownerFactionContentId());
            WorldIoSupport.writeString(out, project.stationArchetypeContentId());
            out.writeLong(project.systemId().value());
            out.writeFloat(project.x());
            out.writeFloat(project.y());
            WorldIoSupport.writeOptionalEntityId(out, project.constructionSiteEntityId());

            WorldIoSupport.writeCount(
                    out, project.materials().size(), MAX_MATERIALS_PER_PROJECT, "constructionMaterials");
            for (ConstructionMaterialState material : project.materials()) {
                WorldIoSupport.writeString(out, material.itemContentId());
                out.writeInt(material.requiredAmount());
                out.writeInt(material.deliveredAmount());
            }

            out.writeLong(project.minimumFundingMilliCredits());
            out.writeLong(project.projectWalletMilliCredits());
            out.writeLong(project.buildDurationTicks());
            WorldIoSupport.writeString(out, project.status().name());
            out.writeLong(project.createdTick());
            out.writeLong(project.stateChangedTick());
            out.writeLong(project.buildStartedTick());
            out.writeLong(project.completedTick());
            WorldIoSupport.writeOptionalEntityId(out, project.completedStationEntityId());
        }
    }

    static List<ConstructionProjectState> read(DataInputStream in) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_PROJECTS, "constructionProjects");
        List<ConstructionProjectState> projects = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ConstructionProjectId id = new ConstructionProjectId(in.readLong());
            String ownerFactionId = WorldIoSupport.readString(in);
            String stationArchetypeId = WorldIoSupport.readString(in);
            StarSystemId systemId = new StarSystemId(in.readLong());
            float x = in.readFloat();
            float y = in.readFloat();
            EntityId siteId = WorldIoSupport.readOptionalEntityId(in);

            int materialCount = WorldIoSupport.readCount(
                    in, MAX_MATERIALS_PER_PROJECT, "constructionMaterials");
            List<ConstructionMaterialState> materials = new ArrayList<>(materialCount);
            for (int materialIndex = 0; materialIndex < materialCount; materialIndex++) {
                materials.add(new ConstructionMaterialState(
                        WorldIoSupport.readString(in),
                        in.readInt(),
                        in.readInt()));
            }

            long minimumFunding = in.readLong();
            long wallet = in.readLong();
            long buildDurationTicks = in.readLong();
            ConstructionProjectStatus status;
            try {
                status = ConstructionProjectStatus.valueOf(WorldIoSupport.readString(in));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown ConstructionProjectStatus", exception);
            }
            long createdTick = in.readLong();
            long stateChangedTick = in.readLong();
            long buildStartedTick = in.readLong();
            long completedTick = in.readLong();
            EntityId stationId = WorldIoSupport.readOptionalEntityId(in);

            projects.add(new ConstructionProjectState(
                    id,
                    ownerFactionId,
                    stationArchetypeId,
                    systemId,
                    x,
                    y,
                    siteId,
                    materials,
                    minimumFunding,
                    wallet,
                    buildDurationTicks,
                    status,
                    createdTick,
                    stateChangedTick,
                    buildStartedTick,
                    completedTick,
                    stationId));
        }
        return List.copyOf(projects);
    }
}
