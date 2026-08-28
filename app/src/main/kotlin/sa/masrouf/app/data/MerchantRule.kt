package sa.masrouf.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A filing decision the user made about a merchant.
 *
 * The built-in merchant list and the type rules together file 83.7% of a real
 * 22,084-record history. The remaining 3,595 are spread over 1,289 local shops and
 * people's names at about two transactions each - things no shipped list could
 * contain, because they are specific to one person's life. So the app learns them:
 * file a merchant once and every transaction from it, past and future, follows. One
 * such decision, on a merchant seen 602 times, filed all 602.
 *
 * Keyed on the folded merchant key rather than the raw name, so "ALDREES",
 * "AL DREES" and "ALDREES 1437 RIYADH" are one merchant, which is the same
 * normalisation deduplication and the built-in rules already use.
 */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey @ColumnInfo(name = "merchant_key") val merchantKey: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
)

@Dao
interface MerchantRuleDao {

    /**
     * Later decisions replace earlier ones. Refiling a merchant is the user
     * correcting themselves, and the correction is what they meant.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("SELECT category_id FROM merchant_rules WHERE merchant_key = :merchantKey")
    suspend fun categoryFor(merchantKey: String): String?

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRule>
}
