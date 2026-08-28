package sa.masrouf.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A filing decision the user made about a merchant.
 *
 * The built-in rules cover about 62% of a real twelve-year history. The rest are
 * one-off local shops and people's names - things no shipped list could contain,
 * because they are specific to one person's life. So the app learns them: file a
 * merchant once and every transaction from it, past and future, follows.
 *
 * Keyed on the folded merchant key rather than the raw name, so "ALDREES",
 * "AL DREES" and "ALDREES 1437 RIYADH" are one merchant, which is the same
 * normalisation deduplication and the built-in rules already use.
 */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
)

@Dao
interface MerchantRuleDao {

    /**
     * Later decisions replace earlier ones. Refiling a merchant is the user
     * correcting themselves, and the correction is what they meant.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("SELECT categoryId FROM merchant_rules WHERE merchantKey = :merchantKey")
    suspend fun categoryFor(merchantKey: String): String?

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRule>
}
