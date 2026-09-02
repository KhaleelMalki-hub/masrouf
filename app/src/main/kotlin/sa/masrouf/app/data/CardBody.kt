package sa.masrouf.app.data

/**
 * One card fragment and one message body, for deciding what kind of card it is.
 *
 * A projection rather than the whole row: the question needs two columns and the
 * history holds twenty-six thousand of them.
 */
data class CardBody(val last4: String, val body: String)
