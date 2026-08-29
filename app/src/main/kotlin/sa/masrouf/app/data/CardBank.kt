package sa.masrouf.app.data

/**
 * Which bank a card belongs to.
 *
 * A card belongs to one bank and keeps belonging to it, so one message that names
 * both answers the question for every record that card ever appears on - including
 * the ones captured before the app recorded a bank at all. That is why this is a
 * lookup built from the data rather than a field the history has to be rewritten
 * to carry.
 */
data class CardBank(val last4: String, val bankId: String)
