package com.mceconomy.company;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.persistence.repo.CompanyBuildingRepository;
import com.mceconomy.world.CompanyHeadquartersPlacer;
import com.mceconomy.world.StructureBuildQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public final class CompanyBuildingService {
	private final CompanyBuildingRepository repository;
	private final CompanyManager companyManager;
	private Map<Integer, CompanyBuildingRepository.CompanyBuilding> buildings = Map.of();

	public CompanyBuildingService(CompanyBuildingRepository repository, CompanyManager companyManager) {
		this.repository = repository;
		this.companyManager = companyManager;
	}

	public void load() throws SQLException {
		buildings = repository.loadAll();
	}

	public int totalPlotCount() {
		return buildings.size();
	}

	public void scheduleHeadquarters(Company company, ServerLevel level, UUID ownerUuid) {
		BlockPos origin = CompanyHeadquartersPlacer.findOrigin(level, company.id());
		String label = company.name() + " HQ";
		StructureBuildQueue.get().enqueue(ownerUuid,
				CompanyHeadquartersPlacer.placer(origin, () -> {
					try {
						repository.save(company.id(), origin, origin.getY());
						buildings = repository.loadAll();
					} catch (SQLException e) {
						com.mceconomy.McEconomyMod.LOGGER.error("HQ kaydi", e);
					}
				}), label);
	}

	public boolean canBuildForOwner(UUID owner) throws SQLException {
		long companies = companyManager.allCompanies().stream()
				.filter(c -> c.ownerUuid().equals(owner)).count();
		if (companies > EconomyConfig.maxCompaniesPerPlayer()) {
			return false;
		}
		var mgr = com.mceconomy.McEconomyMod.getEconomyManager();
		if (mgr.server() == null) {
			return false;
		}
		return StructureBuildQueue.countBuiltPlots(mgr.server()) < EconomyConfig.maxServerBuiltPlots();
	}

	public CompanyBuildingRepository.CompanyBuilding buildingFor(int companyId) {
		return buildings.get(companyId);
	}

	public boolean isProtectedBlock(int x, int y, int z) {
		for (CompanyBuildingRepository.CompanyBuilding b : buildings.values()) {
			if (CompanyHeadquartersPlacer.isProtected(x, y, z, b.origin())) {
				return true;
			}
		}
		return false;
	}
}
