/*
Copyright (C) 2013 Haowen Ning

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package org.liberty.android.fantastischmemo.ui;

import org.liberty.android.fantastischmemo.R;
import org.liberty.android.fantastischmemo.aspect.CheckNullArgs;
import org.liberty.android.fantastischmemo.dao.CardDao;
import org.liberty.android.fantastischmemo.dao.SettingDao;
import org.liberty.android.fantastischmemo.domain.Card;
import org.liberty.android.fantastischmemo.domain.Setting;
import org.liberty.android.fantastischmemo.service.CardPlayerService;


import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

public class CardPlayerActivity extends QACardActivity {
    public static final String EXTRA_START_CARD_ID = "start_card_id";

    public static final String EXTRA_PLAYING_STATUS = "playing_status";

    public static final String EXTRA_RESULT_CARD_ID = "resultCardId";

    private CardDao cardDao;

    private SettingDao settingDao;

    private CardPlayerService cardPlayerService;

    /* Settings */
    private Setting setting;

    private int startCardId = 1;

    private long totalCardCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        startCardId = extras.getInt(EXTRA_START_CARD_ID, -1);

        // showAutoSpeakFragment();

    }

    @Override
    public int getContentView() {
        return R.layout.qa_card_layout_card_player;
    }

    // Make sure the serviceEventListener broadcast receiver
    // is registered at onResume and unregistered at onPause
    // because we do not care about the UI being updated from the
    // CardPlayerService if it is not visible to the user.
    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CardPlayerService.ACTION_GO_TO_CARD);
        registerReceiver(serviceEventListener, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(serviceEventListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindCardPlayerService();
    }

    @Override
    public void onInit() throws Exception {
        cardDao = getDbOpenHelper().getCardDao();
        settingDao = getDbOpenHelper().getSettingDao();

        setting = settingDao.queryForId(1);

        /* Run the learnQueue init in a separate thread */
        if (startCardId != -1) {
            setCurrentCard(cardDao.queryForId(startCardId));
        } else {
            setCurrentCard(cardDao.queryFirstOrdinal());
        }

        totalCardCount = cardDao.countOf();

        bindCardPlayerService();
    }

    @Override
    public void onPostInit() {
        if (getCurrentCard() == null) {
            showNoItemDialog();
            return;
        }
        displayCard(true);
        setSmallTitle(getTitle());
        updateTitle();
    }

    @Override
    public void onPostDisplayCard() {
        getCardTTSUtil().stopSpeak();
    }

    public CardPlayerService getCardPlayerService() {
        return cardPlayerService;
    }

    private void updateTitle(){
        if (getCurrentCard() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.total_text) + ": " + totalCardCount + " ");
            sb.append(getString(R.string.id_text) + ": " + getCurrentCard().getId() + " ");
            sb.append(getString(R.string.ordinal_text_short) + ": " + getCurrentCard().getOrdinal() + " ");
            sb.append(getCurrentCard().getCategory().getName());
            setSmallTitle(sb.toString());
        }
    }

    @Override
    protected void onClickAnswerText() {
        onClickAnswerView();
    }

    @Override
    protected void onClickQuestionView() {
        if (setting.getCardStyle() == Setting.CardStyle.DOUBLE_SIDED) {
            displayCard(true);
            // TODO: fragment color
            //buttonsLayout.setBackgroundColor(setting.getAnswerBackgroundColor());
        }
    }

    private void showNoItemDialog(){
        new AlertDialog.Builder(this)
            .setTitle(this.getString(R.string.memo_no_item_title))
            .setMessage(this.getString(R.string.memo_no_item_message))
            .setNeutralButton(getString(R.string.back_menu_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    /* Finish the current activity and go back to the last activity.
                     * It should be the open screen. */
                    finish();
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener(){
                public void onCancel(DialogInterface dialog){
                    finish();
                }
            })
            .create()
            .show();
    }

    @CheckNullArgs
    protected void gotoCard(Card card) {
        Card currentCard = getCurrentCard();
        if (currentCard.getOrdinal() > card.getOrdinal()) {
            // This is previoius card
            setAnimation(R.anim.slide_right_in, R.anim.slide_right_out);
        } else if (currentCard.getOrdinal() < card.getOrdinal()) {
            setAnimation(R.anim.slide_left_in, R.anim.slide_left_out);
        } else {
            // Do nothing if the new card is equal ot the current card id
        }
        setCurrentCard(card);

        updateCardFrontSide();
        updateTitle();

        // Set animation back
        setAnimation(R.anim.slide_left_in, R.anim.slide_left_out);
    }

    @Override
    public void onBackPressed() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_CARD_ID, getCurrentCard().getId());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // Query out the card id and display that card
    protected void gotoCardId(final int cardId) {
        Card card = cardDao.queryForId(cardId);
        gotoCard(card);
    }

    /*
     * Show the front side of the current card
     * This method is called instead directly update the flashcard
     * so both single and double sided card will work.
     */
    private void updateCardFrontSide(){
        if(getCurrentCard() != null){
            if(setting.getCardStyle() == Setting.CardStyle.DOUBLE_SIDED){
                /* Double sided card, show front */
                displayCard(false);
                // buttonsLayout.setBackgroundColor(setting.getQuestionBackgroundColor());
            } else {
                /* Single sided, show both answer and questjion. */
                displayCard(true);
                // buttonsLayout.setBackgroundColor(setting.getAnswerBackgroundColor());
            }
        }
    }

    private void bindCardPlayerService() {
        Intent intent = new Intent(this, CardPlayerService.class);
        intent.putExtra(CardPlayerService.EXTRA_DBPATH, getDbPath());
        intent.putExtra(CardPlayerService.EXTRA_CURRENT_CARD_ID, getCurrentCard().getId());
        bindService(intent, cardPlayerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindCardPlayerService() {
        unbindService(cardPlayerServiceConnection);
    }

    private ServiceConnection cardPlayerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            CardPlayerService.LocalBinder localBinder = (CardPlayerService.LocalBinder) binder;

            cardPlayerService = localBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            cardPlayerService = null;
        }
    };

    /*
     * This broadcast receiver receive the ACTION_GO_TO_CARD sent from
     * CardPlayerService. It will go to a specific card based on the extras
     * in received intent.
     */
    private BroadcastReceiver serviceEventListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(CardPlayerService.ACTION_GO_TO_CARD)) {
                Bundle extras = intent.getExtras();
                assert extras != null : "The intent received must have card id and playing status"; 
                int currentCardId = extras.getInt(CardPlayerService.EXTRA_CURRENT_CARD_ID);
                // boolean isPlaying = extras.getBoolean(CardPlayerService.EXTRA_IS_PLAYING);

                // TODO: Need to make playButton working
                // playButton.setSelected(isPlaying);

                // 1. Make sure the activity is foreground to update the card.
                // 2. Only update the card if the card is different.
                // So the background service will continue to work with this callback
                // being called.
                if (isActivityForeground() && currentCardId != getCurrentCard().getId()) {
                    gotoCardId(currentCardId);
                }
            }
        }
    };

}
