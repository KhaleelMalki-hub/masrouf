package sa.masrouf.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.ui.graphics.vector.ImageVector
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories

/**
 * One Material Symbol per category.
 *
 * The answer to "show me what shop this was" that works for every row rather than
 * the handful with a famous logo. Merchant logos were considered and refused: they
 * are trademarks, the app has no network to fetch them and no wish for one, and a
 * reference app that tried them fell back to a category glyph on three rows in
 * five anyway.
 *
 * Outlined, not filled: a filled glyph in the category's colour reads as a badge
 * competing with the amount. Outlined at 20dp inside a tinted disc reads as a
 * label.
 */
val Category?.icon: ImageVector
    get() = when (this?.id) {
        SaudiCategories.FOOD.id -> Icons.Outlined.Restaurant
        SaudiCategories.GROCERIES.id -> Icons.Outlined.ShoppingCart
        SaudiCategories.TRANSPORT.id -> Icons.Outlined.DirectionsCar
        SaudiCategories.HOUSING.id -> Icons.Outlined.HomeWork
        SaudiCategories.BILLS.id -> Icons.Outlined.ReceiptLong
        SaudiCategories.HEALTH.id -> Icons.Outlined.LocalHospital
        SaudiCategories.EDUCATION.id -> Icons.Outlined.School
        SaudiCategories.SHOPPING.id -> Icons.Outlined.ShoppingBag
        SaudiCategories.SERVICES.id -> Icons.Outlined.LocalLaundryService
        SaudiCategories.TRAVEL.id -> Icons.Outlined.Flight
        SaudiCategories.BONUS.id -> Icons.Outlined.Star
        SaudiCategories.ENTERTAINMENT.id -> Icons.Outlined.TheaterComedy
        SaudiCategories.FEES.id -> Icons.Outlined.WorkOutline
        SaudiCategories.CHARITY.id -> Icons.Outlined.Favorite
        SaudiCategories.CASH.id -> Icons.Outlined.Payments
        SaudiCategories.TRANSFERS.id -> Icons.Outlined.SwapHoriz
        SaudiCategories.INVESTMENT.id -> Icons.Outlined.TrendingUp
        SaudiCategories.INCOME.id -> Icons.Outlined.AccountBalance
        SaudiCategories.OTHER.id -> Icons.Outlined.Category
        else -> Icons.Outlined.HelpOutline
    }
