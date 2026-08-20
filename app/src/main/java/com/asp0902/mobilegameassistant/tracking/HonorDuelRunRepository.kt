package com.asp0902.mobilegameassistant.tracking

import android.content.Context
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asp0902.mobilegameassistant.analysis.ArtifactXp
import com.asp0902.mobilegameassistant.analysis.HeaderField
import com.asp0902.mobilegameassistant.analysis.HeroRarity
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.OcrBlock
import com.asp0902.mobilegameassistant.analysis.OwnedHeroState
import com.asp0902.mobilegameassistant.analysis.PromotionGaugeObservation
import com.asp0902.mobilegameassistant.analysis.RecognitionSource
import com.asp0902.mobilegameassistant.analysis.ScreenType
import com.asp0902.mobilegameassistant.analysis.ShopItemState
import com.asp0902.mobilegameassistant.analysis.ShopItemType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "honor_duel_snapshots")
data class HonorDuelSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val snapshotId: Long = 0,
    val runId: Long,
    val timestamp: Long,
    val screenType: String,
    val rawRecognitionPayload: String,
    val reconciledPayload: String,
)

@Entity(tableName = "honor_duel_actions")
data class HonorDuelActionEntity(
    @PrimaryKey val actionId: String,
    val runId: Long,
    val timestamp: Long,
    val heroId: String,
    val quantity: Int,
    val cost: Int,
    val expectedProgress: Int?,
    val expectedRequired: Int?,
    val resolved: Boolean = false,
    val conflict: Boolean = false,
)

@Dao
interface HonorDuelSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: HonorDuelSnapshotEntity): Long

    @Query("SELECT * FROM honor_duel_snapshots ORDER BY timestamp DESC, snapshotId DESC LIMIT 1")
    suspend fun latest(): HonorDuelSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAction(action: HonorDuelActionEntity): Long

    @Query("SELECT * FROM honor_duel_actions WHERE runId = :runId AND resolved = 0")
    suspend fun pendingActions(runId: Long): List<HonorDuelActionEntity>

    @Query("UPDATE honor_duel_actions SET resolved = 1, conflict = :conflict WHERE actionId = :actionId")
    suspend fun resolveAction(actionId: String, conflict: Boolean)
}

@Database(entities = [HonorDuelSnapshotEntity::class, HonorDuelActionEntity::class], version = 2, exportSchema = false)
abstract class HonorDuelDatabase : RoomDatabase() {
    abstract fun snapshots(): HonorDuelSnapshotDao
}

@Singleton
class HonorDuelRunRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val snapshots = Room.databaseBuilder(context, HonorDuelDatabase::class.java, "honor_duel.db")
        .fallbackToDestructiveMigration(true)
        .build()
        .snapshots()

    suspend fun save(state: ReconciledHonorDuelState): Long = snapshots.insert(HonorDuelSnapshotEntity(
        runId = state.runId,
        timestamp = System.currentTimeMillis(),
        screenType = state.analysis.screenType.name,
        rawRecognitionPayload = state.analysis.ocrBlocks.joinToString("\n") { "${it.left},${it.top},${it.right},${it.bottom}:${it.text}" },
        reconciledPayload = SnapshotCodec.encode(state),
    ))

    suspend fun restoreLatest(): ReconciledHonorDuelState? = snapshots.latest()?.let { entity ->
        SnapshotCodec.decode(entity.runId, entity.reconciledPayload)
    }

    suspend fun savePurchase(runId: Long, action: HeroPurchaseAction, prediction: PurchasePrediction): Boolean =
        snapshots.insertAction(HonorDuelActionEntity(
            actionId = action.actionId,
            runId = runId,
            timestamp = System.currentTimeMillis(),
            heroId = action.heroId,
            quantity = action.quantity,
            cost = action.cost,
            expectedProgress = prediction.expectedProgress?.progress,
            expectedRequired = prediction.expectedProgress?.required,
        )) != -1L

    suspend fun reconcilePurchases(runId: Long, observed: HonorDuelShopAnalysis) {
        snapshots.pendingActions(runId).forEach { action ->
            val hero = observed.ownedHeroes.firstOrNull { it.heroId == action.heroId }
            if (hero != null && hero.confidence >= .9f) {
                val conflict = hero.promotion.progress != action.expectedProgress || hero.promotion.required != action.expectedRequired
                snapshots.resolveAction(action.actionId, conflict)
            }
        }
    }
}

private object SnapshotCodec {
    fun encode(state: ReconciledHonorDuelState): String = buildList {
        val header = state.analysis.header
        add("screen=${state.analysis.screenType.name},${state.analysis.screenConfidence}")
        add("header=${csv(header.currency, header.shopLevel, header.targetWins, header.artifactXp?.current, header.artifactXp?.required, header.wins, header.hp, header.refreshCost, header.currentRound, escape(header.artifactName))}")
        add("sources=${state.headerSources.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }}")
        add("items=${state.analysis.shopItems.joinToString(";") { item -> csv(item.slotIndex, item.itemType.name, item.price, item.artifactXpAmount, item.confidence, escape(item.heroId), escape(item.heroName), escape(item.faction), item.quantity, item.heroRarity.name, item.isTrialCard, escape(item.equipmentName), item.recognitionSource.name) }}")
        add("heroes=${state.analysis.ownedHeroes.joinToString(";") { hero -> csv(hero.slotIndex, escape(hero.heroId), escape(hero.heroName), escape(hero.faction), hero.rarity.name, hero.promotion.progress, hero.promotion.required, hero.promotion.isMaxRank, escape(hero.equipmentName), hero.sellValue, hero.confidence) }}")
    }.joinToString("\n")

    fun decode(runId: Long, payload: String): ReconciledHonorDuelState? = runCatching {
        val values = payload.lineSequence().mapNotNull { line -> line.substringBefore('=').takeIf { '=' in line }?.let { it to line.substringAfter('=') } }.toMap()
        val screen = values["screen"]?.split(',') ?: return null
        val header = values["header"]?.split(',') ?: return null
        val sources = values["sources"].orEmpty().split(',').mapNotNull { raw -> raw.split(':').takeIf { it.size == 2 }?.let { HeaderField.entries.firstOrNull { field -> field.name == it[0] }?.let { field -> field to ReconciliationSource.valueOf(it[1]) } } }.toMap()
        val items = values["items"].orEmpty().split(';').filter(String::isNotBlank).mapNotNull(::decodeItem)
        val heroes = values["heroes"].orEmpty().split(';').filter(String::isNotBlank).mapNotNull(::decodeHero)
        val analysis = HonorDuelShopAnalysis(
            screenType = ScreenType.valueOf(screen[0]),
            screenConfidence = screen.getOrNull(1)?.toFloatOrNull() ?: 0f,
            header = HonorDuelHeader(
                currency = header.getOrNull(0)?.toIntOrNull(), shopLevel = header.getOrNull(1)?.toIntOrNull(), targetWins = header.getOrNull(2)?.toIntOrNull(),
                artifactXp = header.getOrNull(3)?.toIntOrNull()?.let { current -> header.getOrNull(4)?.toIntOrNull()?.let { ArtifactXp(current, it) } },
                wins = header.getOrNull(5)?.toIntOrNull(), hp = header.getOrNull(6)?.toIntOrNull(), refreshCost = header.getOrNull(7)?.toIntOrNull(),
                currentRound = header.getOrNull(8)?.toIntOrNull(), artifactName = unescape(header.getOrNull(9)),
            ),
            shopItems = items,
            ownedHeroes = heroes,
            ocrBlocks = emptyList(),
        )
        ReconciledHonorDuelState(runId, analysis, sources)
    }.getOrNull()

    private fun decodeItem(raw: String): ShopItemState? = raw.split(',').let { value -> runCatching {
        ShopItemState(value[0].toInt(), ShopItemType.valueOf(value[1]), value[2].toIntOrNull(), value[3].toIntOrNull(), value[4].toFloat(),
            heroId = unescape(value.getOrNull(5)), heroName = unescape(value.getOrNull(6)), faction = unescape(value.getOrNull(7)), quantity = value.getOrNull(8)?.toIntOrNull(),
            heroRarity = value.getOrNull(9)?.let(HeroRarity::valueOf) ?: HeroRarity.UNKNOWN, isTrialCard = value.getOrNull(10).toBoolean(),
            equipmentName = unescape(value.getOrNull(11)), recognitionSource = value.getOrNull(12)?.let(RecognitionSource::valueOf) ?: RecognitionSource.AUTO)
    }.getOrNull() }

    private fun decodeHero(raw: String): OwnedHeroState? = raw.split(',').let { value -> runCatching {
        OwnedHeroState(value[0].toInt(), unescape(value.getOrNull(1)), unescape(value.getOrNull(2)), unescape(value.getOrNull(3)),
            value.getOrNull(4)?.let(HeroRarity::valueOf) ?: HeroRarity.UNKNOWN,
            PromotionGaugeObservation(value.getOrNull(5)?.toIntOrNull(), value.getOrNull(6)?.toIntOrNull(), value.getOrNull(7).toBoolean()),
            unescape(value.getOrNull(8)), value.getOrNull(9)?.toIntOrNull(), value.getOrNull(10)?.toFloatOrNull() ?: 0f)
    }.getOrNull() }

    private fun escape(value: String?) = value?.let { Base64.encodeToString(it.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }.orEmpty()
    private fun unescape(value: String?) = value?.takeIf(String::isNotEmpty)?.let { String(Base64.decode(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)) }
    private fun csv(vararg values: Any?) = values.joinToString(",") { it?.toString().orEmpty() }
}
