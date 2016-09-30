package com.mapswithme.maps.widget.placepage;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import com.mapswithme.maps.R;
import com.mapswithme.maps.bookmarks.data.MapObject;
import com.mapswithme.maps.widget.placepage.PlacePageView.State;
import com.mapswithme.util.UiUtils;
import com.mapswithme.util.concurrency.UiThread;

class BottomPlacePageAnimationController extends BasePlacePageAnimationController
{
  @SuppressWarnings("unused")
  private static final String TAG = BottomPlacePageAnimationController.class.getSimpleName();
  private static final float DETAIL_RATIO = 0.7f;
  private static final float SCROLL_DELTA = 50.0f;
  private final ViewGroup mLayoutToolbar;

  private ValueAnimator mCurrentAnimator;

  private boolean mIsGestureStartedInsideView;
  private boolean mIsGestureFinished;

  private float mDetailMaxHeight;
  private float mScrollDelta;

  private MotionEvent mLastMoveEvent;


  BottomPlacePageAnimationController(@NonNull PlacePageView placePage)
  {
    super(placePage);
    mLayoutToolbar = (LinearLayout) mPlacePage.findViewById(R.id.toolbar_layout);
    if (mLayoutToolbar == null)
      return;

    final Toolbar toolbar = (Toolbar) mLayoutToolbar.findViewById(R.id.toolbar);
    if (toolbar != null)
    {
      UiUtils.showHomeUpButton(toolbar);
      toolbar.setNavigationOnClickListener(new View.OnClickListener()
      {
        @Override
        public void onClick(View v)
        {
          mPlacePage.hide();
        }
      });
    }

    DisplayMetrics dm = placePage.getResources().getDisplayMetrics();
    float screenHeight = dm.heightPixels;
    mDetailMaxHeight = screenHeight * DETAIL_RATIO;
    mScrollDelta = SCROLL_DELTA * dm.density;

    mPreview.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        if (mPlacePage.getState() == State.PREVIEW)
        {
          if (isDetailContentScrollable())
          {
            mPlacePage.setState(State.DETAILS);
          } else
          {
            mPlacePage.setState(State.FULLSCREEN);
          }
        } else
        {
          mPlacePage.setState(State.PREVIEW);
        }
      }
    });
  }

  @Override
  protected boolean onInterceptTouchEvent(MotionEvent event)
  {
    switch (event.getAction())
    {
      case MotionEvent.ACTION_DOWN:
        if (!isInsideView(event.getY()))
        {
          mIsGestureStartedInsideView = false;
          break;
        }

        mIsGestureStartedInsideView = true;
        mIsDragging = false;
        mIsGestureFinished = false;
        mDownCoord = event.getY();
        break;
      case MotionEvent.ACTION_MOVE:
        if (!mIsGestureStartedInsideView)
          break;

        final float delta = mDownCoord - event.getY();
        if (Math.abs(delta) > mTouchSlop && !isDetailsScroll(delta))
          return true;

        mLastMoveEvent = MotionEvent.obtain(event);

        break;
    }

    return false;
  }

  @Override
  protected boolean onTouchEvent(@NonNull MotionEvent event)
  {
    if (mIsGestureFinished)
      return false;

    final boolean finishedDrag = (mIsDragging &&
                                  (event.getAction() == MotionEvent.ACTION_UP ||
                                   event.getAction() == MotionEvent.ACTION_CANCEL));
    if (!mIsGestureStartedInsideView ||
        !isInsideView(event.getY()) ||
        finishedDrag)
    {
      mIsGestureFinished = true;
      finishDrag(mDownCoord - event.getY());
      return false;
    }

    super.onTouchEvent(event);
    return true;
  }

  @Override
  public void onScroll(int left, int top)
  {
    super.onScroll(left, top);

    if (mPlacePage.getState() == State.FULLSCREEN && mCurrentScrollY == 0)
    {
      mDownCoord = mLastMoveEvent.getY();
      mPlacePage.dispatchTouchEvent(MotionEvent.obtain(mLastMoveEvent.getDownTime(),
                                                       mLastMoveEvent.getEventTime(),
                                                       MotionEvent.ACTION_UP,
                                                       mLastMoveEvent.getX(),
                                                       mLastMoveEvent.getY(),
                                                       mLastMoveEvent.getMetaState()));
      mPlacePage.dispatchTouchEvent(MotionEvent.obtain(mLastMoveEvent.getDownTime(),
                                                       mLastMoveEvent.getEventTime(),
                                                       MotionEvent.ACTION_DOWN,
                                                       mLastMoveEvent.getX(),
                                                       mLastMoveEvent.getY(),
                                                       mLastMoveEvent.getMetaState()));
      mPlacePage.dispatchTouchEvent(mLastMoveEvent);
    }
    refreshToolbarVisibility();
  }

  private boolean isInsideView(float y)
  {
//    return y > mPreview.getY() && y < mButtons.getY();
    int[] location = new int[2];
    mDetailsScroll.getLocationOnScreen(location);
    float top = (float) location[1];
    float bottom = top + mDetailsScroll.getHeight();
    return y > top && y < bottom;
  }

  /**
   * @return whether gesture is scrolling of details content(and not dragging PP itself).
   */
  private boolean isDetailsScroll(float delta)
  {
    return isDetailsScrollable() && canScroll(delta);
  }

  private boolean isDetailsScrollable()
  {
    return mPlacePage.getState() == State.FULLSCREEN
           && isDetailContentScrollable();
  }

  private boolean isDetailContentScrollable()
  {
    return mDetailsScroll.getHeight() < mDetailsContent.getHeight();
  }

  private boolean canScroll(float delta)
  {
    return mDetailsScroll.getScrollY() != 0 || delta > 0;
  }

  @Override
  protected void initGestureDetector()
  {
    mGestureDetector = new GestureDetectorCompat(mPlacePage.getContext(), new GestureDetector.SimpleOnGestureListener()
    {
      private static final int X_TO_Y_SCROLL_RATIO = 2;

      @Override
      public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
      {
        final boolean isVertical = Math.abs(distanceY) > X_TO_Y_SCROLL_RATIO * Math.abs(distanceX);

        if (isVertical)
        {
          mIsDragging = true;
          if (!translateBy(-distanceY))
          {
            mDownCoord = e2.getY();
            mPlacePage.dispatchTouchEvent(MotionEvent.obtain(e1.getDownTime(),
                                                             e1.getEventTime(),
                                                             MotionEvent.ACTION_UP,
                                                             e2.getX(),
                                                             e2.getY(),
                                                             e1.getMetaState()));
            mPlacePage.dispatchTouchEvent(MotionEvent.obtain(e1.getDownTime(),
                                                             e1.getEventTime(),
                                                             MotionEvent.ACTION_DOWN,
                                                             e2.getX(),
                                                             e2.getY(),
                                                             e1.getMetaState()));
            mPlacePage.dispatchTouchEvent(e2);
          }
        }

        return true;
      }

      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
      {
        if (velocityY < 0)
        {
          if (mPlacePage.getState() == State.PREVIEW)
          {
            if (isDetailContentScrollable())
              mPlacePage.setState(State.DETAILS);
            else
              mPlacePage.setState(State.FULLSCREEN);
          }
          else if (mPlacePage.getState() == State.DETAILS)
          {
            mPlacePage.setState(State.FULLSCREEN);
          }
        }
        else
        {
          if (mPlacePage.getState() == State.FULLSCREEN)
          {
            if (isDetailContentScrollable())
              mPlacePage.setState(State.DETAILS);
            else
              mPlacePage.setState(State.HIDDEN);
          } else
          {
            mPlacePage.setState(State.HIDDEN);
          }
        }
        return super.onFling(e1, e2, velocityX, velocityY);
      }
    });
    mDetailsScroll.useGestureDetector(mGestureDetector);
  }

  private void finishDrag(float distance)
  {
    final float currentTranslation = mDetailsScroll.getTranslationY();
    if (currentTranslation > mDetailsScroll.getHeight())
    {
      mPlacePage.setState(State.HIDDEN);
      return;
    }

    if (distance >= 0.0f)
    {
      if (mPlacePage.getState() == State.PREVIEW)
      {
        if (isDetailContentScrollable())
        {
          mPlacePage.setState(State.DETAILS);
        }
        else
        {
          mPlacePage.setState(State.FULLSCREEN);
        }
      }
      else if (mPlacePage.getState() != State.FULLSCREEN)
      {
        mPlacePage.setState(State.FULLSCREEN);
      }
    }
    else
    {
      if (mPlacePage.getState() == State.FULLSCREEN)
      {
        if (isDetailContentScrollable())
        {
          mPlacePage.setState(State.DETAILS);
        }
        else
        {
          mPlacePage.setState(State.PREVIEW);
        }
      }
      else if (mPlacePage.getState() == State.DETAILS)
      {
        mPlacePage.setState(State.PREVIEW);
      }
      else
      {
        mPlacePage.setState(State.HIDDEN);
      }
    }
  }

  private boolean translateBy(float distanceY)
  {
    if (Math.abs(distanceY) > mScrollDelta)
      distanceY = 0.0f;
    float detailsTranslation = mDetailsScroll.getTranslationY() + distanceY;
    final boolean isScrollable = isDetailContentScrollable();
    boolean consumeEvent = true;
    if ((isScrollable && detailsTranslation < 0.0f)
        || detailsTranslation < mDetailsScroll.getHeight() - mDetailsContent.getHeight())
    {
      if (isScrollable)
      {
        detailsTranslation = 0.0f;
        mDetailsScroll.useGestureDetector(null);
      }
      else
        detailsTranslation = mDetailsScroll.getHeight() - mDetailsContent.getHeight();

      mState = State.FULLSCREEN;
      consumeEvent = false;
    }

    mDetailsScroll.setTranslationY(detailsTranslation);
    return consumeEvent;
  }

  @Override
  protected void onStateChanged(final State currentState, final State newState, @MapObject.MapObjectType int type)
  {
    prepareYTranslations(newState, type);

    mDetailsScroll.useGestureDetector(mGestureDetector);
    mPlacePage.post(new Runnable()
    {
      @Override
      public void run()
      {
        endRunningAnimation();
        switch (newState)
        {
          case HIDDEN:
            hidePlacePage();
            break;
          case PREVIEW:
            showPreview(currentState);
            break;
          case DETAILS:
            showDetails();
            break;
          case FULLSCREEN:
            if (isDetailContentScrollable())
              mDetailsScroll.useGestureDetector(null);
            showFullscreen();
            break;
        }
      }
    });
  }

  /**
   * Prepares widgets for animating, places them vertically accordingly to their supposed
   * positions.
   */
  private void prepareYTranslations(State newState, @MapObject.MapObjectType int type)
  {
    switch (newState)
    {
      case PREVIEW:
        if (mState == State.HIDDEN)
        {
          UiUtils.invisible(mPlacePage, mPreview, mDetailsFrame, mButtons);
          UiUtils.showIf(type == MapObject.BOOKMARK, mBookmarkDetails);
          mPlacePage.post(new Runnable()
          {
            @Override
            public void run()
            {
              mDetailsScroll.setTranslationY(mDetailsScroll.getHeight());
              mButtons.setTranslationY(mPreview.getHeight() + mButtons.getHeight());

              UiUtils.show(mPlacePage, mPreview, mButtons, mDetailsFrame);
            }
          });
        }
        break;
      case DETAILS:
        UiUtils.show(mPlacePage, mPreview, mDetailsFrame);
        UiUtils.showIf(type == MapObject.BOOKMARK, mBookmarkDetails);
        break;
    }
  }

  private void showPreview(final State currentState)
  {
    if (mLayoutToolbar != null)
      UiUtils.hide(mLayoutToolbar);

    final float translation = mDetailsScroll.getHeight() - mPreview.getHeight();
    mCurrentAnimator = ValueAnimator.ofFloat(mDetailsScroll.getTranslationY(), translation);
    mCurrentAnimator.addUpdateListener(new UpdateListener());
    Interpolator interpolator;
    if (currentState == State.HIDDEN)
    {
      interpolator = new OvershootInterpolator();
      ValueAnimator buttonAnimator = ValueAnimator.ofFloat(mButtons.getTranslationY(), 0);
      buttonAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
      {
        @Override
        public void onAnimationUpdate(ValueAnimator animation)
        {
          mButtons.setTranslationY((Float) animation.getAnimatedValue());
        }
      });
      startDefaultAnimator(buttonAnimator, interpolator);
    } else
    {
      interpolator = new AccelerateInterpolator();
    }

    mCurrentAnimator.addListener(new UiUtils.SimpleAnimatorListener()
    {
      @Override
      public void onAnimationEnd(Animator animation)
      {
        notifyVisibilityListener(true, false);
      }
    });

    startDefaultAnimator(mCurrentAnimator, interpolator);
  }

  private void showDetails()
  {
    if (isDetailContentScrollable())
      mCurrentAnimator = ValueAnimator.ofFloat(mDetailsScroll.getTranslationY(),
                                               mDetailsScroll.getHeight() - mDetailMaxHeight + mButtons.getHeight());
    else
      mCurrentAnimator = ValueAnimator.ofFloat(mDetailsScroll.getTranslationY(),
                                               mDetailsScroll.getHeight() - mDetailsContent.getHeight());
    mCurrentAnimator.addUpdateListener(new UpdateListener());
    mCurrentAnimator.addListener(new AnimationListener());

    startDefaultAnimator();
  }

  private void showFullscreen()
  {
    if (isDetailContentScrollable())
      mCurrentAnimator = ValueAnimator.ofFloat(mDetailsScroll.getTranslationY(), 0);
    else
      mCurrentAnimator = ValueAnimator.ofFloat(mDetailsScroll.getTranslationY(),
                                               mDetailsScroll.getHeight() - mDetailsContent.getHeight());
    mCurrentAnimator.addUpdateListener(new UpdateListener());
    mCurrentAnimator.addListener(new AnimationListener());

    startDefaultAnimator();
  }

  @SuppressLint("NewApi")
  private void hidePlacePage()
  {
    if (mLayoutToolbar != null)
      UiUtils.hide(mLayoutToolbar);

    final float animHeight = mPlacePage.getHeight() - mPreview.getY();
    mCurrentAnimator = ValueAnimator.ofFloat(0f, animHeight);
    mCurrentAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
    {
      @Override
      public void onAnimationUpdate(ValueAnimator animation)
      {
        mPlacePage.setTranslationY((Float) animation.getAnimatedValue());
        notifyProgress();
      }
    });
    mCurrentAnimator.addListener(new UiUtils.SimpleAnimatorListener()
    {
      @Override
      public void onAnimationEnd(Animator animation)
      {
        initialVisibility();
        mPlacePage.setTranslationY(0);
        notifyProgress();
        notifyVisibilityListener(false, false);
      }
    });

    startDefaultAnimator();
  }

  private void startDefaultAnimator()
  {
    startDefaultAnimator(mCurrentAnimator);
  }

  private void endRunningAnimation()
  {
    if (mCurrentAnimator != null && mCurrentAnimator.isRunning())
    {
      mCurrentAnimator.removeAllUpdateListeners();
      mCurrentAnimator.removeAllListeners();
      mCurrentAnimator.end();
      mCurrentAnimator = null;
    }
  }

  private void refreshToolbarVisibility()
  {
    if (mLayoutToolbar != null)
      UiThread.runLater(new Runnable()
      {
        @Override
        public void run()
        {
          UiUtils.showIf(mCurrentScrollY > 0, mLayoutToolbar);
        }
      });
  }

  private void notifyProgress()
  {
    notifyProgress(0, mPreview.getTranslationY());
  }

  private class UpdateListener implements ValueAnimator.AnimatorUpdateListener
  {
    @Override
    public void onAnimationUpdate(ValueAnimator animation)
    {
      float translationY = (Float) animation.getAnimatedValue();
      mDetailsScroll.setTranslationY(translationY);
      notifyProgress();
    }
  }

  private class AnimationListener extends UiUtils.SimpleAnimatorListener
  {
    @Override
    public void onAnimationEnd(Animator animation)
    {
      refreshToolbarVisibility();
      notifyVisibilityListener(true, true);
      mDetailsScroll.scrollTo(0, 0);
      notifyProgress();
    }
  }
}
