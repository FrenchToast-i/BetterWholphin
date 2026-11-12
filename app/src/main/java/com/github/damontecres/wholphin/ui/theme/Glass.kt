package com.github.damontecres.wholphin.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Shapes
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.opacity
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow

/**
 * Whether the Liquid Glass interface is active.
 *
 * Provided once near the root of the app so every screen can opt into the glass look
 * without threading the preference through each composable.
 */
val LocalLiquidGlass = compositionLocalOf { false }

/**
 * The layer that glass surfaces sample from.
 *
 * A `null` backdrop means there is nothing to refract, so glass surfaces fall back to a
 * translucent tint. This happens on Android < 12 (no real-time blur) and for content that is
 * rendered outside the main shell, such as dialogs.
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Whether the artwork behind the glass is itself bright.
 *
 * A clear pane carries white labels over a dark frame and disappears into a white one: the
 * material is the artwork, so the artwork decides whether type holds. Panes that carry copy
 * read this and take on a light smoke when the palette behind them is pale, which is the same
 * adjustment iOS makes between the clear and tinted variants of its materials.
 */
val LocalBackdropLight = compositionLocalOf { false }

/**
 * Mean luminance of the artwork behind the glass, 0–1. Zero means the frame is unknown or dark.
 *
 * Glass is transparent by design, which makes white type a bet on whatever happens to be behind
 * it. A boolean "pale" flag answers only the extreme case and leaves the middle unaddressed: a
 * mid-grey frame with detail in it passes for dark, takes no smoke, and the small copy on the
 * pane sits at the edge of legibility. The material therefore ramps its smoke with this number
 * instead of switching it on at a threshold.
 */
val LocalBackdropLuminance = compositionLocalOf { 0f }

/**
 * The shape scale for glass mode.
 *
 * Material's TV scale is nearly square: a focused settings row is a hard-edged block, and the
 * default `Surface` radius is small enough to read as a rectangle. Glass has no sharp corners, so
 * the whole scale is restated around the material's radii. Components that set their own shape
 * (panels, cards, pills) are unaffected; this is what catches the ones that do not.
 */
val GlassShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )

/**
 * Shared measurements for Liquid Glass mode.
 *
 * The numbers follow tvOS more than Material: generous corner radii, a tall tab bar, and airy
 * page insets. On a 1080p television these are pixels, so a 4dp change is visible from the sofa.
 */
object GlassTokens {
    /** Large chrome: hero, settings sheet, dialogs. */
    val panelRadius: Dp = 30.dp
    val panelShape: Shape = RoundedCornerShape(panelRadius)

    /** Media cards. Small enough to keep a grid dense, large enough to read as glass. */
    val cardRadius: Dp = 16.dp

    /** Pills: tabs, buttons, chips. */
    val pillRadius: Dp = 100.dp

    /** Inset of page content and floating chrome from the screen edge. */
    val screenInset: Dp = 34.dp

    /**
     * Height of the floating top bar.
     *
     * One height for the whole row. The destinations capsule used to be the tall slab
     * ([pillHeight] + 16dp) with every other control 50dp beside it, which read as two ranks of
     * chrome that meant nothing: a library is not more important than search. The bar is a row of
     * equal capsules, so it measures like one.
     */
    val barHeight: Dp = 50.dp

    /** Inset of the top bar from the top edge. */
    val barTopInset: Dp = 20.dp

    /** Gap between the top bar and page content. */
    val barGap: Dp = 28.dp

    /** Space page content leaves at the top for the bar. */
    val contentTop: Dp = barTopInset + barHeight + barGap

    /** Height and horizontal padding of one tab inside the bar. */
    val pillHeight: Dp = 50.dp
    val pillPadding: Dp = 14.dp

    /**
     * A chip: one remembered query under the search field.
     *
     * Shorter than a bar pill because it sits in the page rather than in the chrome, and the row
     * it lives in has to leave room for the discovery shelf underneath it. The padding is wider
     * than a tab's so the query reads as a single object rather than as a button with a word in
     * it.
     */
    val chipHeight: Dp = 44.dp
    val chipPadding: Dp = 20.dp

    /** Width of the full-page search capsule. Wide enough to invite typing on a TV. */
    val searchWidth: Dp = 520.dp

    /** Diameter of the floating search button that hovers over every page. */
    val searchButtonSize: Dp = 56.dp

    /** Gap between separate capsules in the bar. */
    val capsuleGap: Dp = 12.dp

    /**
     * A profile or server disc on the setup screens.
     *
     * tvOS profile pickers are small enough that four or five of them fit on one line under a
     * short title. The classic theme's 129dp disc is sized for a phone-shaped carousel; at that
     * size four profiles plus "Add User" overflow a sheet, which is exactly what happened.
     */
    val profileSize: Dp = 104.dp

    /** Space between profile discs, and between a disc and its label. */
    val profileGap: Dp = 18.dp
    val profileLabelGap: Dp = 10.dp

    /**
     * Backdrop blur, one of the material's own effects.
     *
     * Liquid Glass is frosted, not a clear window: iOS glass visibly softens whatever is behind
     * it and then bends it at the rim. The blur is deliberately small — enough to separate the
     * pane from the artwork, not enough to turn a photograph into a flat wash. The lens, the rim
     * light and the inner shade carry the material from there.
     */
    val panelBlur: Dp = 10.dp
    val barBlur: Dp = 8.dp

    /**
     * Lens strength: the material's `lens` effect.
     *
     * Glass is a lens: whatever sits behind a thick slab is magnified. This is the single most
     * important difference between liquid glass and a blurred rectangle — the artwork behind the
     * pane is larger than the artwork around it, and the bend reaches its maximum at the rim.
     *
     * `refractionHeight` is how far the edge bend reaches into the pane, `refractionAmount` the
     * peak displacement at the rim. The amount is negative: the shader samples inward, so the
     * artwork is pulled toward the centre and enlarged rather than pushed off the edge.
     *
     * The band has to stay well under half the pane's short side, or it stops being an edge and
     * turns the whole face into a magnifying glass that smears the copy sitting on it.
     */
    val refractionHeight: Dp = 13.dp
    val refractionAmount: Dp = (-24).dp

    /** A capsule is only 66dp tall, so its bend stays inside the caps. */
    val pillRefractionHeight: Dp = 10.dp
    val pillRefractionAmount: Dp = (-16).dp

    /**
     * The specular rim, as the library's [Highlight].
     *
     * This is the material's own edge: a soft stroke lit on the two arcs facing the light, not a
     * hand-drawn bevel. `width` is the thickness of the lit band, `blurRadius` how far it bleeds
     * inward, `alpha` the layer opacity (the preset's white is already half-transparent, so the
     * effective light is about half of this).
     */
    val rimWidth: Dp = 2.4.dp
    val rimBlur: Dp = 4.0.dp
    const val RIM_ALPHA = 0.72f

    /**
     * Light gathers where the surface turns away from the viewer (the corners), so the rim is
     * not an even outline: a higher falloff concentrates it on the lit arcs and lets the long
     * sides fall quiet, which is what stops a glass edge from reading as a stroked border.
     */
    private val rimStyle: HighlightStyle = HighlightStyle.Default(angle = 45f, falloff = 1.5f)

    val highlight: Highlight
        get() = Highlight(width = rimWidth, blurRadius = rimBlur, alpha = RIM_ALPHA, style = rimStyle)

    /** The bar capsules are smaller, so their rim is proportionally tighter. */
    val pillHighlight: Highlight
        get() = Highlight(width = 2.0.dp, blurRadius = 3.2.dp, alpha = 0.78f, style = rimStyle)

    /**
     * A focused control's rim: the same sheen, lit harder.
     *
     * Focus on a transparent control cannot be shown with a border (that is the stroked-rectangle
     * look the material is trying to avoid), so the glass itself brightens: the rim gets thicker
     * and hotter and the frost picks up a little more white. On a field or a capsule that reads
     * as the surface turning toward the viewer.
     */
    val focusHighlight: Highlight
        get() = Highlight(width = 2.8.dp, blurRadius = 5.0.dp, alpha = 1f, style = rimStyle)

    /**
     * Inner shade, as the library's [InnerShadow].
     *
     * The offset is negative because the library clears the shape *shifted* by the offset and
     * keeps what is left over: a negative Y leaves a dark band along the bottom inside edge,
     * which is what gives the slab thickness where the light does not reach.
     */
    val innerShadowRadius: Dp = 2.5.dp
    val innerShadowOffset: Dp = 2.0.dp
    const val INNER_SHADOW_ALPHA = 0.26f

    /** How much of the refracted backdrop survives inside a pane. */
    const val BACKDROP_OPACITY = 1f

    /**
     * Tint laid over the refracted backdrop.
     *
     * Nearly zero on purpose: the artwork *is* the material. The reading of glass comes from
     * the edge and the slight lift in saturation, not from a smoked fill.
     */
    val panelTint = Color.Black.copy(alpha = 0.0f)

    /**
     * The hero is the one content pane that carries artwork of its own.
     *
     * A clear pane works for copy, because white type separates from any artwork behind it. A
     * title logo does not: it is drawn in its own colours, so over a frame of the same hue (a
     * Halloween special is literally orange on orange) the logo disappears into the glass and the
     * viewer has to read the small type underneath to know what they are looking at. A third of
     * black puts every logo on a neutral ground while leaving the refracted artwork legible.
     *
     * It stays below [copyTintOnLight], so pale art still smokes harder through the copy rule.
     */
    val heroTint = Color.Black.copy(alpha = 0.30f)

    /**
     * The bar sits over the brightest band of the frame, so it is smoked a little more than a
     * panel — just enough for a white label to hold.
     */
    val barTint = Color.Black.copy(alpha = 0.10f)

    /**
     * Chrome over pale artwork.
     *
     * A capsule's frost *adds* white, which is exactly wrong over a white frame: the bar turns
     * into a pale slab and its labels stop separating from it. The chrome takes a smoke instead,
     * the same way a copy pane does, so the capsule stays darker than the artwork rather than
     * lighter. Chrome never carries a scrim, so it cannot pick this up from `glassSurface`'s copy
     * rule — [glassCapsule] reads the backdrop directly, and also thins its frost, because at 13%
     * the milk would cancel half of the smoke.
     */
    val barTintOnLight = Color.Black.copy(alpha = 0.55f)

    /**
     * Smoke for a pane that carries copy when the frame behind it is pale.
     *
     * Clear glass over a white frame is a white pane, and white labels on it are not readable
     * from a sofa. The smoke is only added when the backdrop's own palette is light, so dark
     * artwork still gets a perfectly clear pane.
     *
     * The number accounts for the frost that sits on top of it: over near-white art, 52% black
     * plus the 4.5% white frost lands around 4:1 against white type (the frost alone would undo a
     * third of the smoke), and the copy scrim adds more down the lower half. Weaker smoke looked
     * like a grey slab *and* left the small type at 2:1.
     */
    val copyTintOnLight = Color.Black.copy(alpha = 0.52f)

    /**
     * The luminance ramp for copy smoke.
     *
     * Below [SMOKE_START] the frame is dark enough that a clear pane is the right material and
     * white type reads. From there to [SMOKE_FULL] the smoke ramps to [copyTintOnLight]. The old
     * model jumped from nothing to 52% at 0.55, which left every busy mid-tone frame — the common
     * case for a television still — in the legibility gap the material was supposed to close.
     */
    const val SMOKE_START = 0.30f
    const val SMOKE_FULL = 0.72f

    /** How much of [copyTintOnLight] a frame of this luminance needs. */
    fun copySmoke(luminance: Float): Float =
        copyTintOnLight.alpha *
            ((luminance - SMOKE_START) / (SMOKE_FULL - SMOKE_START)).coerceIn(0f, 1f)

    /**
     * How much of [barTintOnLight] chrome needs over a frame of this luminance.
     *
     * Chrome starts smoking earlier than content copy: a capsule's frost adds white, so over a
     * merely light frame the labels already lose contrast to the milk, not only to the artwork.
     */
    fun chromeSmoke(luminance: Float): Float =
        ((luminance - 0.22f) / (SMOKE_FULL - 0.22f)).coerceIn(0f, 1f)

    /**
     * Frost: the faint milkiness of real glass.
     *
     * Note the direction — glass *adds* light. Tinting every pane toward black is what makes a
     * clear material read as smoked plastic; a couple of percent of white lifts the pane above
     * the artwork so it looks lit from within.
     */
    const val FROST = 0.045f

    /**
     * Chrome carries more frost than content.
     *
     * The bar floats over the brightest band of the frame and has to hold white labels, so its
     * glass is a thicker mix: a few percent of white lifts it off the artwork the way iOS's
     * navigation material sits above the content it covers. Panels that live *in* the content
     * layer stay clearer, so the artwork behind them survives.
     */
    const val BAR_FROST = 0.13f

    /** Frost of a focused control, slightly above the bar so focus visibly lights the pane. */
    const val FOCUS_FROST = 0.19f

    /** Shadow that lifts a pane off the artwork behind it. Without it glass looks pasted on. */
    val panelElevation: Dp = 18.dp
    val islandElevation: Dp = 12.dp

    /**
     * A settings card: the inset group that holds related rows.
     *
     * iOS groups rows into one rounded container with hairline separators rather than drawing a
     * box around every switch. The fill is deliberately faint — the sheet already carries the
     * glass — so the group reads as a soft inset rather than a second material sitting on the
     * first.
     */
    val rowRadius: Dp = 18.dp
    const val ROW_FILL = 0.055f
    val rowDivider = Color.White.copy(alpha = 0.10f)

    /**
     * How far a settings row's content is inset from the card edge.
     *
     * The card bleeds to the sheet's inner edge; the text does not. Keeping the label one clear
     * inset inside the rounded corner is what makes a group of rows read as one object.
     */
    val rowInset: Dp = 18.dp

    /**
     * A dialog or a long-press menu.
     *
     * Menus are the material seen up close: they cover content instead of sitting in it, so they
     * take a little more body than a page panel (a light smoke that keeps white labels readable
     * over any artwork) and a higher lift, because they are the topmost layer of the interface.
     * The radius, the lens and the rim are the panel's, so a menu still reads as the same glass.
     *
     * The frost is above a content panel's and the tint is much darker: a menu covers a busy grid
     * of posters rather than sitting in a quiet page, and its labels have to hold over whatever is
     * underneath. The smoke is what keeps a title readable over a bright frame.
     */
    val dialogTint = Color.Black.copy(alpha = 0.52f)
    const val DIALOG_FROST = 0.06f
    val dialogElevation: Dp = 30.dp

    /**
     * The "nothing here" message on an empty grid.
     *
     * Unlike a pane that carries artwork of its own, this pill is the *only* copy on the page:
     * if it is unreadable there is no second place to look. It therefore keeps a fixed smoke
     * instead of following [copySmoke], so it holds white type over a bright frame and over a
     * dark one alike, while the backdrop still refracts through it.
     */
    val emptyStateTint = Color.Black.copy(alpha = 0.55f)

    /**
     * A setup sheet: the server and user screens, which float over the ambient colour field
     * rather than over artwork. There is nothing behind them to refract, so the pane is a light
     * smoke on the theme's own gradient — enough body for white type, thin enough that the field
     * still shows through.
     */
    val sheetTint = Color.Black.copy(alpha = 0.16f)
    const val SHEET_SCRIM = 0.34f

    /**
     * The playback controls' deck.
     *
     * The player surface is not part of the Compose layer, so there is nothing for a pane to
     * sample down there: this is the tinted fallback, deliberately lighter than a menu so the
     * picture still reads through it, with the copy scrim keeping the transport icons off the
     * brightest frames.
     */
    val playerTint = Color.Black.copy(alpha = 0.26f)
    const val PLAYER_SCRIM = 0.5f

    /** Whether the device can actually render glass (real time blur needs Android 12+). */
    val canBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** AGSL refraction and the lit rim need Android 13+. */
    val canRefract: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * Draws this element as a pane of liquid glass over the backdrop captured by
 * [LocalGlassBackdrop].
 *
 * The material is the library's own pipeline end to end, in its documented order:
 *  1. `vibrancy` pushes the colours of whatever is behind the pane (iOS saturation boost),
 *  2. `colorControls` gives the slight brightness and contrast lift of a glass face,
 *  3. `blur` frosts the artwork,
 *  4. `lens` bends that image along the rim, which is what makes the pane read as a slab of
 *     glass rather than a frosted rectangle,
 *  5. `opacity` sets how much of the backdrop survives,
 *  6. the library's `Highlight` lights the rim and its `InnerShadow` shades the far edge,
 *  7. the library's `Shadow` lifts the pane off the artwork.
 *
 * Every piece of decoration here is the material's own: no stroked outline, no overlaid bevel
 * gradients, no custom shader. The rim, the inner shade and the drop shadow are the library's
 * decorators, so every pane — hero, dialog, bar capsule — is the same substance under the same
 * lighting model; the only paint this adds is the face (tint, frost, copy scrim).
 *
 * On devices without real-time blur the same call degrades to a translucent pane with the same
 * paint, so the layout still reads as glass instead of turning into a clear window onto the
 * artwork.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassTokens.panelShape,
    enabled: Boolean = LocalLiquidGlass.current,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    blur: Dp = GlassTokens.panelBlur,
    tint: Color = GlassTokens.panelTint,
    refraction: Boolean = true,
    refractionHeight: Dp = GlassTokens.refractionHeight,
    refractionAmount: Dp = GlassTokens.refractionAmount,
    chromaticAberration: Boolean = false,
    rim: Highlight = GlassTokens.highlight,
    frost: Float = GlassTokens.FROST,
    elevation: Dp = GlassTokens.panelElevation,
    scrim: Float = 0f,
): Modifier {
    if (!enabled) return this

    // A pane that carries copy takes a smoke only when the frame behind it is pale, and only when
    // the caller has not already smoked it harder: a menu's 52% tint is dark enough for white
    // labels and must not be thinned down to a copy pane's 38%.
    val smoke = if (scrim > 0f) GlassTokens.copySmoke(LocalBackdropLuminance.current) else 0f
    val surfaceTint =
        if (smoke > tint.alpha) {
            Color.Black.copy(alpha = smoke)
        } else {
            tint
        }

    if (backdrop == null || !GlassTokens.canBlur) {
        // No backdrop to sample (or no real-time blur): the same paint as the sampled path, just
        // opaque enough to hold labels without any artwork behind it. Clipped to the shape, since
        // a rectangular fill behind a 30dp corner is the one thing that gives a fallback away.
        val fill = surfaceTint.copy(alpha = (surfaceTint.alpha + 0.34f).coerceAtMost(0.72f))
        return this
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .drawWithContent {
                drawGlassFill(fill, frost, scrim)
                drawContent()
            }
    }

    val density = LocalDensity.current
    val blurPx = with(density) { blur.toPx() }
    val refractionHeightPx = with(density) { refractionHeight.toPx() }
    val refractionAmountPx = with(density) { refractionAmount.toPx() }
    val refract = refraction && GlassTokens.canRefract

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // Order matters. Vibrancy before the blur: applied after it just over-saturates
            // the smear instead of the colours it came from.
            vibrancy()
            colorControls(brightness = 0.04f, contrast = 1.03f, saturation = 1.20f)
            blur(blurPx)
            if (refract) {
                // A negative amount samples *inward*, which is what a real lens does: the
                // artwork behind the pane gets larger and bends hardest at the rim. The bend is
                // kept to a narrow band normal to the edge (no depthEffect), so it reads as the
                // wall of a thick slab rather than as a gentle radial zoom across the face.
                lens(
                    refractionHeight = refractionHeightPx,
                    refractionAmount = refractionAmountPx,
                    depthEffect = false,
                    chromaticAberration = chromaticAberration,
                )
            }
            opacity(GlassTokens.BACKDROP_OPACITY)
        },
        highlight = { rim },
        shadow = {
            Shadow(
                radius = elevation,
                offset = DpOffset(0.dp, elevation / 5),
                color = Color.Black.copy(alpha = 0.26f),
            )
        },
        innerShadow = {
            InnerShadow(
                radius = GlassTokens.innerShadowRadius,
                offset = DpOffset(0.dp, -GlassTokens.innerShadowOffset),
                color = Color.Black.copy(alpha = GlassTokens.INNER_SHADOW_ALPHA),
            )
        },
        onDrawSurface = {},
    ).drawWithContent {
        // This lives in drawWithContent rather than in `drawBackdrop`'s onDrawSurface because that
        // callback is not composited above a layer backdrop in 0.0.1-alpha02: the surface kept its
        // rim, its lens and its blur while every tint, frost and scrim it was given silently went
        // missing, which is exactly why panes looked like clear windows over pale artwork.
        drawGlassFill(surfaceTint, frost, scrim)
        drawContent()
    }
}

/**
 * The pane's own paint: tint, then frost, then the copy scrim.
 *
 * Shared by the sampled path and the no-backdrop fallback, so both are the same substance rather
 * than two recipes that drift apart. The material supplies the edge, the lens and the light; this
 * supplies only what is laid on the face. It paints *under* the children — drawn after
 * `drawContent()` it would dim every label in the pane.
 */
private fun DrawScope.drawGlassFill(
    tint: Color,
    frost: Float,
    scrim: Float,
) {
    if (tint.alpha > 0f) drawRect(tint)
    if (frost > 0f) drawRect(color = Color.White.copy(alpha = frost))

    // Content scrim. Clear glass over a photograph is a beautiful surface and a terrible
    // background for type, so panels that carry copy darken their lower half and their left edge
    // very slightly. It is a gradient rather than a fill: the top of the pane stays perfectly
    // clear, which is what keeps the material reading as glass instead of smoked plastic.
    if (scrim > 0f) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.34f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.30f * scrim),
                ),
        )
        drawRect(
            brush =
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.20f * scrim),
                    0.34f to Color.Black.copy(alpha = 0.06f * scrim),
                    0.62f to Color.Transparent,
                ),
        )
    }
}

/**
 * A pane of glass to place behind a group of controls.
 *
 * [refraction] bends the backdrop along the panel's rim, which reads as thick glass. It is
 * reserved for large panels since it costs an extra shader pass.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.panelShape,
    refraction: Boolean = true,
    tint: Color = GlassTokens.panelTint,
    blur: Dp = GlassTokens.panelBlur,
    refractionHeight: Dp = GlassTokens.refractionHeight,
    refractionAmount: Dp = GlassTokens.refractionAmount,
    chromaticAberration: Boolean = false,
    rim: Highlight = GlassTokens.highlight,
    frost: Float = GlassTokens.FROST,
    elevation: Dp = GlassTokens.panelElevation,
    scrim: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.glassSurface(
                shape = shape,
                refraction = refraction,
                tint = tint,
                blur = blur,
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                chromaticAberration = chromaticAberration,
                rim = rim,
                frost = frost,
                elevation = elevation,
                scrim = scrim,
            ),
        content = content,
    )
}

/**
 * The material of the floating chrome: a capsule in the top bar, a sub-navigation strip, the search
 * field, the alphabet rail.
 *
 * They are the same substance, so the numbers live here rather than being repeated at each call
 * site. Chrome carries more tint and frost than a content panel because it floats over the
 * brightest band of the frame and has to hold white labels; its lens is tighter because a capsule
 * is only 50–66dp tall, and a panel's 13dp bend would reach past the caps.
 */
@Composable
fun Modifier.glassCapsule(
    shape: Shape = RoundedCornerShape(percent = 50),
    frost: Float = GlassTokens.BAR_FROST,
    elevation: Dp = GlassTokens.islandElevation,
    rim: Highlight = GlassTokens.pillHighlight,
): Modifier {
    val smoke = GlassTokens.chromeSmoke(LocalBackdropLuminance.current)
    return glassSurface(
        shape = shape,
        blur = GlassTokens.barBlur,
        tint = lerp(GlassTokens.barTint, GlassTokens.barTintOnLight, smoke),
        refractionHeight = GlassTokens.pillRefractionHeight,
        refractionAmount = GlassTokens.pillRefractionAmount,
        // No chromatic aberration. The library implements it as seven displaced samples weighted
        // per channel, not as a 1px fringe: on a high-contrast backdrop the red channel lands tens
        // of pixels from the blue one and the capsule fills with coloured streaks. The single-tap
        // refraction is the cleaner read and the cheaper pass.
        chromaticAberration = false,
        elevation = elevation,
        rim = rim,
        // Over pale art the material is smoked rather than milky: the frost is the light that
        // would wash the labels out, so it steps back and the tint carries the contrast.
        frost = frost * (1f - 0.6f * smoke),
    )
}

/**
 * The deck behind the playback controls.
 *
 * A floating pane rather than a full-width bar, so the picture keeps its edges and the deck reads
 * as chrome laid over the film. It passes `backdrop = null` for the same reason dialogs do — but
 * here the cause is the video surface: it is composited by the platform, not by Compose, so the
 * sampled layer holds whatever artwork the page behind the player still has, not the film. The
 * tinted fallback is the honest material over video, and the rim and lift still place it.
 */
@Composable
fun Modifier.glassPlayerDeck(
    shape: Shape = GlassTokens.panelShape,
): Modifier =
    glassSurface(
        shape = shape,
        backdrop = null,
        tint = GlassTokens.playerTint,
        frost = GlassTokens.FROST,
        scrim = GlassTokens.PLAYER_SCRIM,
        elevation = GlassTokens.islandElevation,
    )

/**
 * The backdrop for screens that exist before any artwork does.
 *
 * Setup has no library to draw a backdrop from, and flat black makes every pane of glass look like
 * a grey rectangle. A deep, slowly turning field of the theme's own colours gives the material
 * something to bend, stays quiet enough for white type, and makes the first screen feel like the
 * same app as the library behind it.
 */
@Composable
fun GlassAmbientBackground(modifier: Modifier = Modifier) {
    val palette = getThemeColors(LocalTheme.current).darkScheme
    Box(
        modifier =
            modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to palette.background,
                        0.55f to palette.surfaceVariant.copy(alpha = 0.7f),
                        1f to palette.background,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.primaryContainer.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.18f),
                        radius = size.width * 0.62f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.tertiary.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.82f),
                        radius = size.width * 0.58f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.secondary.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.76f, size.height * 0.16f),
                        radius = size.width * 0.42f,
                    ),
                )
                // A vignette keeps the edges deep, so floating capsules stay the brightest thing.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.28f),
                    ),
                )
            },
    )
}

/**
 * Paints the focused item's palette over the backdrop.
 *
 * Glass needs something to refract. Stock Wholphin keeps the artwork in the top-right corner and
 * fills the rest with an animated wash of the poster's colours; glass mode bleeds the artwork
 * across the whole frame instead, so this wash goes *on top* of it: the artwork keeps its detail,
 * and the frame still visibly shifts to match whatever is focused, which is the part of the stock
 * backdrop worth keeping.
 */
@Composable
fun GlassBackdropWash(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    modifier: Modifier = Modifier,
) {
    val animatedPrimary by animateColorAsState(primary, animationSpec = tween(1250), label = "glass_wash_primary")
    val animatedSecondary by animateColorAsState(secondary, animationSpec = tween(1250), label = "glass_wash_secondary")
    val animatedTertiary by animateColorAsState(tertiary, animationSpec = tween(1250), label = "glass_wash_tertiary")
    Box(
        modifier =
            modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(animatedSecondary.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.75f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(animatedPrimary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.width * 0.75f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(animatedTertiary.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.6f,
                    ),
                )
                // Vignette: keeps the frame edges quiet so floating chrome reads as the top layer.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.06f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f),
                    ),
                )
            },
    )
}
