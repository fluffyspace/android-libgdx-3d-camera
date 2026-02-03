package com.mygdx.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/**
 * TODO: document your custom view class.
 */
class OrientationIndicator : View {

    private var _exampleString: String? = null // TODO: use a default from R.string...
    private var _exampleColor: Int = Color.RED // TODO: use a default from R.color...
    private var _exampleDimension: Float = 0f // TODO: use a default from R.dimen...
    private var _degrees: Float = 0f

    private lateinit var textPaint: TextPaint
    private lateinit var paint: Paint
    private var textWidth: Float = 0f
    private var textHeight: Float = 0f

    /**
     * The text to draw
     */
    var exampleString: String?
        get() = _exampleString
        set(value) {
            _exampleString = value
            invalidateTextPaintAndMeasurements()
        }

    var degrees: Float
        get() = _degrees
        set(value) {
            if(value < 0){
                _degrees = 360 - abs(value)%360
            } else {
                _degrees = value
            }
            invalidateTextPaintAndMeasurements()
        }

    /**
     * The font color
     */
    var exampleColor: Int
        get() = _exampleColor
        set(value) {
            _exampleColor = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * In the example view, this dimension is the font size.
     */
    var exampleDimension: Float
        get() = _exampleDimension
        set(value) {
            _exampleDimension = value
            invalidateTextPaintAndMeasurements()
        }

    /**
     * In the example view, this drawable is drawn above the text.
     */
    var exampleDrawable: Drawable? = null

    lateinit var path: Path

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        // Load attributes
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.OrientationIndicator, defStyle, 0
        )

        _exampleString = a.getString(
            R.styleable.OrientationIndicator_exampleString
        )
        _exampleColor = a.getColor(
            R.styleable.OrientationIndicator_exampleColor,
            exampleColor
        )
        // Use getDimensionPixelSize or getDimensionPixelOffset when dealing with
        // values that should fall on pixel boundaries.
        _exampleDimension = a.getDimension(
            R.styleable.OrientationIndicator_exampleDimension,
            exampleDimension
        )

        if (a.hasValue(R.styleable.OrientationIndicator_exampleDrawable)) {
            exampleDrawable = a.getDrawable(
                R.styleable.OrientationIndicator_exampleDrawable
            )
            exampleDrawable?.callback = this
        }

        a.recycle()

        // Set up a default TextPaint object
        textPaint = TextPaint().apply {
            flags = Paint.ANTI_ALIAS_FLAG
            textAlign = Paint.Align.CENTER
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
        }

        paint = Paint(Paint.ANTI_ALIAS_FLAG);

        paint.strokeWidth = 5f;
        paint.style = Paint.Style.FILL_AND_STROKE;
        paint.isAntiAlias = true;
        paint.color = Color.WHITE
        paint.setShadowLayer(5f, 0f, 0f, Color.BLACK)



        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    }

    private fun invalidateTextPaintAndMeasurements() {
        textPaint.let {
            it.textSize = exampleDimension
            it.color = Color.WHITE//exampleColor
            textWidth = it.measureText(exampleString ?: "")
            textHeight = it.fontMetrics.bottom
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path = Path()
        path.moveTo((width / 2).toFloat(), (height-10).toFloat())
        path.lineTo((width / 2).toFloat()-10, (height).toFloat())
        path.lineTo((width / 2).toFloat()+10, (height).toFloat())
        path.lineTo((width / 2).toFloat(), (height-10).toFloat())
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        val paddingRight = paddingRight
        val paddingBottom = paddingBottom

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom

        canvas.drawARGB(0, 0, 0, 0);

        canvas.drawPath(path, paint);

        val points = 9
        val modulatedDegrees = ((degrees%5)/5f)*(contentWidth/(points))
        var x = 0f
        val ymin = (contentHeight-15).toFloat()
        val ymax = (contentHeight-40).toFloat()
        for(i in 0 until points){
            x  = ((i/points.toFloat())*contentWidth) - modulatedDegrees
            canvas.drawLine(
                x,
                ymin,
                x,
                ymax,
                paint)
        }
        val podijeljeno = (degrees.toInt()/45*45)
        val daleko = (degrees%45)/45f*contentWidth
        val gdje = contentWidth - ((contentWidth/2).toFloat() + daleko) % contentWidth
        canvas.drawText(if(gdje > contentWidth/2) ((podijeljeno + 45) % 360).toString() else podijeljeno.toString(),
            gdje, (contentHeight-50).toFloat(), textPaint)

        /*canvas.drawText(degrees.toString(),
            (contentWidth/3).toFloat(), (contentHeight-20).toFloat(), textPaint)*/

        /*exampleString?.let {
            // Draw the text.
            canvas.drawText(
                it,
                paddingLeft + (contentWidth - textWidth) / 2,
                paddingTop + (contentHeight + textHeight) / 2,
                textPaint
            )
        }

        // Draw the example drawable on top of the text.
        exampleDrawable?.let {
            it.setBounds(
                paddingLeft, paddingTop,
                paddingLeft + contentWidth, paddingTop + contentHeight
            )
            it.draw(canvas)
        }*/
    }
}