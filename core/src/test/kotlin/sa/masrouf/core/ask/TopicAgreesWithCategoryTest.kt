package sa.masrouf.core.ask

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.CategoryGuess
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.model.SaudiCategories
import kotlin.test.assertTrue

/**
 * The two tables that describe the same merchants must not contradict each other.
 *
 * [Topic] answers "how much on pharmacies" and [CategoryGuess] answers "which band
 * does this row belong to", and they are separate lists maintained by hand. Nothing
 * connected them, so a merchant could be a pharmacy to one screen and food to the
 * other - and it was: INNOVATIVE sits in PHARMACY while its rows file as food, so
 * the ask screen counted money the category strip had put somewhere else, and both
 * numbers looked right on their own page.
 *
 * Silence is allowed. A topic keyword with no category rule simply has not been
 * given one, and an unfiled row is honest. A keyword that files as something the
 * topic disagrees with is the defect.
 */
class TopicAgreesWithCategoryTest {

    private val expected = mapOf(
        Topic.FUEL to SaudiCategories.TRANSPORT,
        Topic.COFFEE to SaudiCategories.FOOD,
        Topic.DELIVERY to SaudiCategories.FOOD,
        Topic.PHARMACY to SaudiCategories.HEALTH,
    )

    @Test
    fun `no topic keyword files as a category the topic disagrees with`() {
        val clashes = Topic.entries.flatMap { topic ->
            val wanted = expected.getValue(topic)
            topic.keywords.mapNotNull { keyword ->
                val filed = CategoryGuess.forMerchant(keyword)
                // A topic keyword may be a PREFIX rather than a whole name: the
                // pharmacy topic carries "NMC2" to reach every branch number, while
                // the category list names the three branches this history has seen.
                // Such a keyword matches no merchant on its own, and the question
                // for it is whether the rules it reaches all agree with the topic.
                val reached = CategoryGuess.keywords
                    .filter { (rule, _) -> rule.startsWith(ArabicText.normalizeMerchant(keyword)) }
                when {
                    filed != null && filed.id != wanted.id ->
                        "\"$keyword\" is ${topic.name} but files as ${filed.id}, not ${wanted.id}"
                    reached.any { (_, category) -> category.id != wanted.id } ->
                        "\"$keyword\" is ${topic.name} but reaches " +
                            reached.filter { it.second.id != wanted.id }.joinToString { it.first }
                    // Naming a merchant in a topic is a claim to know what it is.
                    // Knowing that and not knowing its category is the state that
                    // let the ask screen call a shop a pharmacy while the strip put
                    // its money in whatever the transaction TYPE defaults to.
                    filed == null && reached.isEmpty() ->
                        "\"$keyword\" is ${topic.name} but no category rule covers it"
                    else -> null
                }
            }
        }
        assertTrue(clashes.isEmpty(), "topic and category disagree:\n  " + clashes.joinToString("\n  "))
    }

    /** Every topic needs a category, or the check above silently stops covering it. */
    @Test
    fun `every topic states the category it belongs to`() {
        assertTrue(
            expected.keys.containsAll(Topic.entries.toSet()),
            "a Topic with no expected category: ${Topic.entries - expected.keys}",
        )
    }
}
