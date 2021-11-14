package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.abs

/** 현재 선택된 값 변경 리스너 */
interface OnValueChangeListener {
    fun onValueChange(oldVal: String, newVal: String)
}

/** 스크롤 변경 감지 리스너 */
interface OnScrollListener {
    fun onScrollStateChange(scrollState: Int)

    companion object {
        const val SCROLL_STATE_IDLE = 0 // 스크롤 상태가 아님
        const val SCROLL_STATE_TOUCH_SCROLL = 1 // 스크롤 중
        const val SCROLL_STATE_FLING = 2 // 스크롤 플링이벤트 감지
    }
}

/** 번호를 선택할 수 있는 WheelView이다. */

class WheelPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val DEFAULT_ITEM_COUNT = 5 // 디폴트 아이템 갯수
    private val DEFAULT_TEXT_SIZE = 100 // 디폴트 글자 크기
    private var mScrollState = OnScrollListener.SCROLL_STATE_IDLE // 스크롤 디폴트 상태 ( 스크롤 중이 아님 )
    private var mWrapSelectorWheelPreferred: Boolean

    // 상, 하단 아이템 투명도 조절
    private val TOP_ITEM_OPACITY = 0.9f // 뷰 상단 아이템 투명도 값 ( 첫번째 보이는 아이템 )
    private val BOTTOM_ITEM_OPACITY = 0.9f // 뷰 하단 아이템 투명도 값 ( 마지막에 보이는 아이템 )

    // 속도 조절
    private val ITEM_CLICK_CHANGE_DARATION = 300 // 아이템을 클릭했을때, 해당 아이템으로 스크롤 되는 시간
    private val SCROLL_MAX_VELOCITY = 3000 // 스크롤 최대 속도, 높을수록 빨라진다.
    private val SCROLL_MIN_VELOCITY = 500 // 스크롤 최소 속도

    // 텍스트 정보
    private var mTextPaint: Paint = Paint() // 텍스트를 그리기 위한 페인트 객체
    private var mTextSize: Int // 텍스트 사이즈
    private var mTextAlign: String // 텍스트 배치
    private var mSelectedTextColor: Int // 선택된 텍스트의 색상
    private var mUnSelectedTextColor: Int // 선택안된 텍스트의 색상
    private var mTextHeight: Int = 0 // 텍스트의 높이

    // 셀렉터 정보
    private var mSelectorItemCount: Int // 셀렉터에 있는 아이템 갯수
    private var mSelectorVisibleItemCount: Int // 셀렉터에 보이는 아이템 갯수
    private var mSelectorMiddleItemIndex: Int // 셀렉터 가운데 인덱스(고정값) = (mSelectorItemCount - 1) / 2
    private var mSelectorItemIndices: ArrayList<Int> // 셀렉터에 있는 데이터 리스트 = 아이템 갯수만큼 고정이다.

    // 리스트(아이템) 정보
    private var mCurSelectedItemIndex = 0 // 현재 선택된 아이템의 인덱스
    private var mMinIndex: Int // 리스트의 최소값 인덱스
    private var mMaxIndex: Int // 리스트의 최대값 인덱스
    private var mMaxValidIndex: Int? = null // 리스트의 최소값 인덱스에 값이 있는지
    private var mMinValidIndex: Int? = null // 리스트의 최대값 인덱스에 값이 있는지

    // 뷰 터치 관련
    private var mIsDragging: Boolean = false // 아이템을 드래깅중인지 여부
    private val mTouchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop } // 의도하지 않은 스크롤을 줄이는데 사용, slop은 영어로 "출렁거림" 이다. touch를 했을때 출렁거림 정도를 판단한다.


    private var mOverScroller: OverScroller? = null
    private var mVelocityTracker: VelocityTracker? = null
    private var mLastY: Float = 0f
    private var mCurrentFirstItemOffset: Int = 0
    private var mInitialFirstItemOffset = Int.MIN_VALUE
    private var mTextGapHeight: Int = 0
    private var mItemHeight: Int = 0
    private var mPreviousScrollerY: Int = 0
    private var mOnValueChangeListener: OnValueChangeListener? = null
    private var mOnScrollListener: OnScrollListener? = null
    private var mAdapter: DayAdapter? = null
    private var mFadingEdgeEnabled = true
    private var mSelectedTextScale = 0.3f
    private var mTypefaceIndex: Int = 0


    init {
        val attributesArray =
            context.obtainStyledAttributes(attrs, R.styleable.WheelPicker, defStyleAttr, 0)

        // 셀렉터 정보 초기화
        mSelectorItemCount =
            attributesArray.getInt(R.styleable.WheelPicker_wheelItemCount, DEFAULT_ITEM_COUNT) + 2
        mSelectorVisibleItemCount = mSelectorItemCount - 2
        mSelectorItemIndices = ArrayList(mSelectorItemCount)
        mSelectorMiddleItemIndex = (mSelectorVisibleItemCount - 1) / 2

        mMinIndex = attributesArray.getInt(R.styleable.WheelPicker_min, Integer.MIN_VALUE)
        mMaxIndex = attributesArray.getInt(R.styleable.WheelPicker_max, Integer.MAX_VALUE)
        if (attributesArray.hasValue(R.styleable.WheelPicker_maxValidIndex))
            mMaxValidIndex = attributesArray.getInt(R.styleable.WheelPicker_maxValidIndex, 0)
        if (attributesArray.hasValue(R.styleable.WheelPicker_minValidIndex))
            mMinValidIndex = attributesArray.getInt(R.styleable.WheelPicker_minValidIndex, 0)
        mWrapSelectorWheelPreferred =
            attributesArray.getBoolean(R.styleable.WheelPicker_wrapSelectorWheel, false)
        mSelectedTextScale =
            attributesArray.getFloat(R.styleable.WheelPicker_selectedTextScale, 0.3f)

        mOverScroller = OverScroller(context, DecelerateInterpolator(2.5f))

        mSelectedTextColor = attributesArray.getColor(
            R.styleable.WheelPicker_selectedTextColor, ContextCompat.getColor(context, R.color.blue)
        )
        mUnSelectedTextColor = attributesArray.getColor(
            R.styleable.WheelPicker_textColor, ContextCompat.getColor(context, R.color.red)
        )
        mTextSize = attributesArray.getDimensionPixelSize(
            R.styleable.WheelPicker_textSize,
            DEFAULT_TEXT_SIZE
        )
        val textAlignInt = attributesArray.getInt(R.styleable.WheelPicker_align, 1)
        mTextAlign = when (textAlignInt) {
            0 -> "LEFT"
            1 -> "CENTER"
            2 -> "RIGHT"
            else -> "CENTER"
        }
        mFadingEdgeEnabled =
            attributesArray.getBoolean(R.styleable.WheelPicker_fadingEdgeEnabled, true)
        mTypefaceIndex = attributesArray.getInt(R.styleable.WheelPicker_typeface, 0);

        mTextPaint.run {
            isAntiAlias = true
            isAntiAlias = true
            textSize = mTextSize.toFloat()
            textAlign = Paint.Align.valueOf(mTextAlign)
            style = Paint.Style.FILL_AND_STROKE
            typeface =
                when (mTypefaceIndex) { // for the constant values please check the WheelPicker_typeface in the attrs.xml
                    0 -> Typeface.DEFAULT
                    1 -> Typeface.SANS_SERIF
                    2 -> Typeface.SERIF
                    else -> Typeface.DEFAULT
                }
        }

        attributesArray.recycle()

        initializeSelectorWheelIndices()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            // need to do all this when we know our size
            initializeSelectorWheel()
            initializeFadingEdges()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Try greedily to fit the max width and height.
        var lp: ViewGroup.LayoutParams? = layoutParams
        if (lp == null)
            lp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

        var width = calculateSize(suggestedMinimumWidth, lp.width, widthMeasureSpec)
        var height = calculateSize(suggestedMinimumHeight, lp.height, heightMeasureSpec)

        width += paddingLeft + paddingRight
        height += paddingTop + paddingBottom


        setMeasuredDimension(width, height)
    }

    override fun getSuggestedMinimumHeight(): Int {
        var suggested = super.getSuggestedMinimumWidth()
        if (mSelectorVisibleItemCount > 0) {
            val fontMetricsInt = mTextPaint.fontMetricsInt
            val height = fontMetricsInt.descent - fontMetricsInt.ascent
            suggested = Math.max(suggested, height * mSelectorVisibleItemCount)
        }
        return suggested
    }

    private fun calculateSize(suggestedSize: Int, paramSize: Int, measureSpec: Int): Int {
        var result = 0
        val size = MeasureSpec.getSize(measureSpec)
        val mode = MeasureSpec.getMode(measureSpec)

        when (MeasureSpec.getMode(mode)) {
            MeasureSpec.AT_MOST ->

                if (paramSize == ViewGroup.LayoutParams.WRAP_CONTENT)
                    result = Math.min(suggestedSize, size)
                else if (paramSize == ViewGroup.LayoutParams.MATCH_PARENT)
                    result = size
                else {
                    result = Math.min(paramSize, size)
                }
            MeasureSpec.EXACTLY -> result = size
            MeasureSpec.UNSPECIFIED ->

                result =
                    if (paramSize == ViewGroup.LayoutParams.WRAP_CONTENT || paramSize == ViewGroup.LayoutParams
                            .MATCH_PARENT
                    )
                        suggestedSize
                    else {
                        paramSize
                    }
        }

        return result
    }

    private fun initializeSelectorWheel() {
        mItemHeight = getItemHeight()
        mTextHeight = computeTextHeight()
        mTextGapHeight = getGapHeight()

        val visibleMiddleItemPos =
            mItemHeight * mSelectorMiddleItemIndex + (mItemHeight + mTextHeight) / 2
        mInitialFirstItemOffset = visibleMiddleItemPos - mItemHeight * mSelectorMiddleItemIndex
        mCurrentFirstItemOffset = mInitialFirstItemOffset
    }

    private fun initializeFadingEdges() {
        isVerticalFadingEdgeEnabled = mFadingEdgeEnabled
        if (mFadingEdgeEnabled)
            setFadingEdgeLength((bottom - top - mTextSize) / 2)
    }

    private fun initializeSelectorWheelIndices() {
        mSelectorItemIndices.clear()

        mCurSelectedItemIndex = if (mMinValidIndex == null || mMinValidIndex!! < mMinIndex) {
            if (mMinIndex <= 0) {
                0
            } else {
                mMinIndex
            }
        } else {
            if (mMinValidIndex!! <= 0) {
                0
            } else {
                mMinValidIndex!!
            }
        }

        for (i in 0 until mSelectorItemCount) {
            var selectorIndex = mCurSelectedItemIndex + (i - mSelectorMiddleItemIndex)
            if (mWrapSelectorWheelPreferred) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex)
            }
            mSelectorItemIndices.add(selectorIndex)
        }
    }

    /** 하단 아이템 영역 투명도 설정 */
    override fun getBottomFadingEdgeStrength(): Float {
        return BOTTOM_ITEM_OPACITY
    }

    /** 상단 아이템 영역 투명도 설정 */
    override fun getTopFadingEdgeStrength(): Float {
        return TOP_ITEM_OPACITY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawVertical(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onTouchEventVertical(event)
        return true
    }

    private val TAG = "asdasdasd"

    private fun onTouchEventVertical(event: MotionEvent) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }

        mVelocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!mOverScroller!!.isFinished)
                    mOverScroller!!.forceFinished(true)

                mLastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = event.y - mLastY
                if (!mIsDragging && abs(deltaY) > mTouchSlop) {
                    parent?.requestDisallowInterceptTouchEvent(true)

                    if (deltaY > 0) {
                        deltaY -= mTouchSlop
                    } else {
                        deltaY += mTouchSlop
                    }
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                    mIsDragging = true
                }

                if (mIsDragging) {
                    scrollBy(0, deltaY.toInt())
                    invalidate()
                    mLastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mIsDragging) {
                    mIsDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)

                    mVelocityTracker?.computeCurrentVelocity(1000, SCROLL_MAX_VELOCITY.toFloat())
                    val velocity = mVelocityTracker?.yVelocity?.toInt()

                    if (abs(velocity!!) > SCROLL_MIN_VELOCITY) {
                        mPreviousScrollerY = 0
                        mOverScroller?.fling(
                            scrollX, scrollY, 0, velocity, 0, 0, Integer.MIN_VALUE,
                            Integer.MAX_VALUE, 0, (getItemHeight() * 0.7).toInt()
                        )
                        invalidateOnAnimation()
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING)
                    }
                    recyclerVelocityTracker()
                } else {
                    //click event
                    val y = event.y.toInt()
                    handlerClickVertical(y)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mIsDragging) {
                    mIsDragging = false
                }
                recyclerVelocityTracker()
            }
        }
    }

    private fun handlerClickVertical(y: Int) {
        val selectorIndexOffset = y / mItemHeight - mSelectorMiddleItemIndex
        changeValueBySteps(selectorIndexOffset)
    }

    override fun scrollBy(x: Int, y: Int) {
        if (y == 0)
            return

        val gap = mTextGapHeight

        if (!mWrapSelectorWheelPreferred && y > 0
            && (mSelectorItemIndices[mSelectorMiddleItemIndex] <= mMinIndex
                    || (mMinValidIndex != null && mSelectorItemIndices[mSelectorMiddleItemIndex] <= mMinValidIndex!!))
        ) {
            if (mCurrentFirstItemOffset + y - mInitialFirstItemOffset < gap / 2)
                mCurrentFirstItemOffset += y
            else {
                mCurrentFirstItemOffset = mInitialFirstItemOffset + (gap / 2)
                if (!mOverScroller!!.isFinished && !mIsDragging) {
                    mOverScroller!!.abortAnimation()
                }
            }
            return
        }

        if (!mWrapSelectorWheelPreferred && y < 0
            && (mSelectorItemIndices[mSelectorMiddleItemIndex] >= mMaxIndex
                    || (mMaxValidIndex != null && mSelectorItemIndices[mSelectorMiddleItemIndex] >= mMaxValidIndex!!))
        ) {
            if (mCurrentFirstItemOffset + y - mInitialFirstItemOffset > -(gap / 2))
                mCurrentFirstItemOffset += y
            else {
                mCurrentFirstItemOffset = mInitialFirstItemOffset - (gap / 2)
                if (!mOverScroller!!.isFinished && !mIsDragging) {
                    mOverScroller!!.abortAnimation()
                }
            }
            return
        }

        mCurrentFirstItemOffset += y

        while (mCurrentFirstItemOffset - mInitialFirstItemOffset < -gap) {
            mCurrentFirstItemOffset += mItemHeight
            increaseSelectorsIndex()
            if (!mWrapSelectorWheelPreferred
                && (mSelectorItemIndices[mSelectorMiddleItemIndex] >= mMaxIndex
                        || (mMaxValidIndex != null && mSelectorItemIndices[mSelectorMiddleItemIndex] >= mMaxValidIndex!!))
            ) {
                mCurrentFirstItemOffset = mInitialFirstItemOffset
            }
        }

        while (mCurrentFirstItemOffset - mInitialFirstItemOffset > gap) {
            mCurrentFirstItemOffset -= mItemHeight
            decreaseSelectorsIndex()
            if (!mWrapSelectorWheelPreferred
                && (mSelectorItemIndices[mSelectorMiddleItemIndex] <= mMinIndex
                        || (mMinValidIndex != null && mSelectorItemIndices[mSelectorMiddleItemIndex] <= mMinValidIndex!!))
            ) {
                mCurrentFirstItemOffset = mInitialFirstItemOffset
            }
        }
        onSelectionChanged(mSelectorItemIndices[mSelectorMiddleItemIndex], true)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (mOverScroller!!.computeScrollOffset()) {
            val x = mOverScroller!!.currX
            val y = mOverScroller!!.currY


            if (mPreviousScrollerY == 0) {
                mPreviousScrollerY = mOverScroller!!.startY
            }
            scrollBy(x, y - mPreviousScrollerY)
            mPreviousScrollerY = y
            invalidate()
        } else {
            if (!mIsDragging)
            //align item
                adjustItemVertical()
        }
    }

    private fun adjustItemVertical() {
        mPreviousScrollerY = 0
        var deltaY = mInitialFirstItemOffset - mCurrentFirstItemOffset

        if (Math.abs(deltaY) > mItemHeight / 2) {
            deltaY += if (deltaY > 0)
                -mItemHeight
            else
                mItemHeight
        }

        if (deltaY != 0) {
            mOverScroller!!.startScroll(scrollX, scrollY, 0, deltaY, 800)
            invalidateOnAnimation()
        }

        onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
    }

    private fun recyclerVelocityTracker() {
        mVelocityTracker?.recycle()
        mVelocityTracker = null
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
    }

    private fun onScrollStateChange(scrollState: Int) {
        if (mScrollState == scrollState) {
            return
        }
        mScrollState = scrollState
        mOnScrollListener?.onScrollStateChange(scrollState)
    }

    private fun getItemHeight(): Int {
        return height / (mSelectorItemCount - 2)
    }

    private fun getGapHeight(): Int {
        return getItemHeight() - computeTextHeight()
    }

    /** 텍스트의 높이 계산 */
    private fun computeTextHeight(): Int {
        val metricsInt = mTextPaint.fontMetricsInt
        return abs(metricsInt.bottom + metricsInt.top)
    }

    private fun invalidateOnAnimation() {
        postInvalidateOnAnimation()
    }

    private fun drawVertical(canvas: Canvas) {
        if (mSelectorItemIndices.size == 0)
            return
        val itemHeight = getItemHeight()

        val x = when (mTextPaint.textAlign) {
            Paint.Align.LEFT -> paddingLeft.toFloat()
            Paint.Align.CENTER -> ((right - left) / 2).toFloat()
            Paint.Align.RIGHT -> (right - left).toFloat() - paddingRight.toFloat()
            else -> ((right - left) / 2).toFloat()
        }

        var y = mCurrentFirstItemOffset.toFloat()

        var i = 0

        val topIndexDiffToMid = mSelectorMiddleItemIndex
        val bottomIndexDiffToMid = mSelectorVisibleItemCount - mSelectorMiddleItemIndex - 1
        val maxIndexDiffToMid = Math.max(topIndexDiffToMid, bottomIndexDiffToMid)

        while (i < mSelectorItemIndices.size) {
            var scale = 1f

            val offsetToMiddle =
                Math.abs(y - (mInitialFirstItemOffset + mSelectorMiddleItemIndex * itemHeight).toFloat())

            if (maxIndexDiffToMid != 0)
                scale =
                    mSelectedTextScale * (itemHeight * maxIndexDiffToMid - offsetToMiddle) / (itemHeight * maxIndexDiffToMid) + 1

            if (offsetToMiddle < mItemHeight / 2) {
                mTextPaint.color = mSelectedTextColor
            } else {
                mTextPaint.color = mUnSelectedTextColor
            }

            canvas.save()
            canvas.scale(scale, scale, x, y)
            canvas.drawText(getValue(mSelectorItemIndices[i]), x, y, mTextPaint)
            canvas.restore()

            y += itemHeight
            i++
        }
    }

    private fun getPosition(value: String): Int = when {
        mAdapter != null -> {
            validatePosition(mAdapter!!.getPosition(value))
        }
        else -> try {
            val position = value.toInt()
            validatePosition(position)
        } catch (e: NumberFormatException) {
            0
        }
    }

    private fun increaseSelectorsIndex() {
        for (i in 0 until (mSelectorItemIndices.size - 1)) {
            mSelectorItemIndices[i] = mSelectorItemIndices[i + 1]
        }
        var nextScrollSelectorIndex = mSelectorItemIndices[mSelectorItemIndices.size - 2] + 1
        if (mWrapSelectorWheelPreferred && nextScrollSelectorIndex > mMaxIndex) {
            nextScrollSelectorIndex = mMinIndex
        }
        mSelectorItemIndices[mSelectorItemIndices.size - 1] = nextScrollSelectorIndex
    }

    private fun decreaseSelectorsIndex() {
        for (i in mSelectorItemIndices.size - 1 downTo 1) {
            mSelectorItemIndices[i] = mSelectorItemIndices[i - 1]
        }
        var nextScrollSelectorIndex = mSelectorItemIndices[1] - 1
        if (mWrapSelectorWheelPreferred && nextScrollSelectorIndex < mMinIndex) {
            nextScrollSelectorIndex = mMaxIndex
        }
        mSelectorItemIndices[0] = nextScrollSelectorIndex
    }

    private fun changeValueBySteps(steps: Int) {
        mPreviousScrollerY = 0
        mOverScroller!!.startScroll(0, 0, 0, -mItemHeight * steps, ITEM_CLICK_CHANGE_DARATION)
        invalidate()
    }

    private fun onSelectionChanged(current: Int, notifyChange: Boolean) {
        val previous = mCurSelectedItemIndex
        mCurSelectedItemIndex = current
        if (notifyChange && previous != current) {
            notifyChange(previous, current)
        }
    }

    private fun getWrappedSelectorIndex(selectorIndex: Int): Int {
        if (selectorIndex > mMaxIndex) {
            return mMinIndex + (selectorIndex - mMaxIndex) % (mMaxIndex - mMinIndex + 1) - 1
        } else if (selectorIndex < mMinIndex) {
            return mMaxIndex - (mMinIndex - selectorIndex) % (mMaxIndex - mMinIndex + 1) + 1
        }
        return selectorIndex
    }

    private fun notifyChange(previous: Int, current: Int) {
        mOnValueChangeListener?.onValueChange(getValue(previous), getValue(current))
    }

    private fun validatePosition(position: Int): Int {
        return if (!mWrapSelectorWheelPreferred) {
            when {
                mMaxValidIndex == null && position > mMaxIndex -> mMaxIndex
                mMaxValidIndex != null && position > mMaxValidIndex!! -> mMaxValidIndex!!
                mMinValidIndex == null && position < mMinIndex -> mMinIndex
                mMinValidIndex != null && position < mMinValidIndex!! -> mMinValidIndex!!
                else -> position
            }
        } else {
            getWrappedSelectorIndex(position)
        }
    }

    fun scrollTo(position: Int) {
        if (mCurSelectedItemIndex == position)
            return

        mCurSelectedItemIndex = position
        mSelectorItemIndices.clear()
        for (i in 0 until mSelectorItemCount) {
            var selectorIndex = mCurSelectedItemIndex + (i - mSelectorMiddleItemIndex)
            if (mWrapSelectorWheelPreferred) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex)
            }
            mSelectorItemIndices.add(selectorIndex)
        }

        invalidate()
    }

    fun setOnValueChangedListener(onValueChangeListener: OnValueChangeListener) {
        mOnValueChangeListener = onValueChangeListener
    }

    fun setOnScrollListener(onScrollListener: OnScrollListener) {
        mOnScrollListener = onScrollListener
    }

    fun smoothScrollTo(position: Int) {
        val realPosition = validatePosition(position)
        changeValueBySteps(realPosition - mCurSelectedItemIndex)
    }

    fun smoothScrollToValue(value: String) {
        smoothScrollTo(getPosition(value))
    }

    fun scrollToValue(value: String) {
        scrollTo(getPosition(value))
    }

    fun setUnselectedTextColor(resourceId: Int) {
        mSelectedTextColor = resourceId
    }

    /**
     * Set user define adapter
     *
     * @adapter user define adapter
     * @indexRangeBasedOnAdapterSize specific if the picker's min~max range is based on adapter's size
     */
    fun setAdapter(adapter: DayAdapter, indexRangeBasedOnAdapterSize: Boolean = true) {
        mAdapter = adapter
        if (mAdapter == null) {
            initializeSelectorWheelIndices()
            invalidate()
            return
        }

        if (adapter.getSize() != -1 && indexRangeBasedOnAdapterSize) {
            mMaxIndex = adapter.getSize() - 1
            mMinIndex = 0
        }

        mMaxValidIndex = adapter.getMaxIndex()
        mMinValidIndex = adapter.getMinIndex()

        initializeSelectorWheelIndices()
        invalidate()
    }

    /**
     * Set a custom typeface object for the text
     *
     * @param typeface the custom typeface object
     */
    fun setTypeface(typeface: Typeface) {
        mTextPaint.typeface = typeface
    }

    /**
     * Sets whether the selector wheel shown during flinging/scrolling should
     * wrap around the {@link NumberPicker#getMinValue()} and
     * {@link NumberPicker#getMaxValue()} values.
     * <p>
     * By default if the range (max - min) is more than the number of items shown
     * on the selector wheel the selector wheel wrapping is enabled.
     * </p>
     * <p>
     * <strong>Note:</strong> If the number of items, i.e. the range (
     * {@link #getMaxValue()} - {@link #getMinValue()}) is less than
     * the number of items shown on the selector wheel, the selector wheel will
     * not wrap. Hence, in such a case calling this method is a NOP.
     * </p>
     *
     * @param wrapSelectorWheel Whether to wrap.
     */
    fun setWrapSelectorWheel(wrap: Boolean) {
        mWrapSelectorWheelPreferred = wrap
        invalidate()
    }

    /**
     * Gets whether the selector wheel wraps when reaching the min/max value.
     *
     * @return True if the selector wheel wraps.
     *
     * @see .getMinValue
     * @see .getMaxValue
     */
    fun getWrapSelectorWheel(): Boolean {
        return mWrapSelectorWheelPreferred
    }

    /**
     * Set how many visible item show in the picker
     */
    fun setWheelItemCount(count: Int) {
        mSelectorItemCount = count + 2
        mSelectorVisibleItemCount = mSelectorItemCount - 2
        mSelectorMiddleItemIndex = (mSelectorVisibleItemCount - 1) / 2
        mSelectorItemIndices = ArrayList(mSelectorItemCount)
        reset()
        invalidate()
    }

    /**
     * Set color for current selected item
     */
    fun setSelectedTextColor(colorId: Int) {
        mSelectedTextColor = ContextCompat.getColor(context, colorId)
        invalidate()
    }

    fun getValue(position: Int): String = when {
        mAdapter != null -> mAdapter!!.getValue(position)
        else -> if (!mWrapSelectorWheelPreferred) {
            when {
                position > mMaxIndex -> ""
                position < mMinIndex -> ""
                else -> position.toString()
            }
        } else {
            getWrappedSelectorIndex(position).toString()
        }
    }

    fun setValue(value: String) {
        scrollToValue(value)
    }

    fun setMaxValue(max: Int) {
        mMaxIndex = max
    }

    fun getMaxValue(): String {
        return if (mAdapter != null) {
            mAdapter!!.getValue(mMaxIndex)
        } else {
            mMaxIndex.toString()
        }
    }

    fun setMinValue(min: Int) {
        mMinIndex = min
    }

    fun setMinValidValue(minValid: Int?) {
        mMinValidIndex = minValid
    }

    fun setMaxValidValue(maxValid: Int?) {
        mMaxValidIndex = maxValid
    }

    fun getMinValue(): String {
        return if (mAdapter != null) {
            mAdapter!!.getValue(mMinIndex)
        } else {
            mMinIndex.toString()
        }
    }

    fun reset() {
        initializeSelectorWheelIndices()
        initializeSelectorWheel()
        invalidate()
    }

    fun getCurrentItem(): String {
        return getValue(mCurSelectedItemIndex)
    }
}


internal fun Int.clamp(min: Int, max: Int): Int {
    if (this < min) return min
    return if (this > max) max else this
}