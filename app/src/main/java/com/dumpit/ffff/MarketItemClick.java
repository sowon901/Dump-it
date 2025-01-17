package com.dumpit.ffff;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kakao.kakaolink.v2.KakaoLinkResponse;
import com.kakao.kakaolink.v2.KakaoLinkService;
import com.kakao.message.template.ButtonObject;
import com.kakao.message.template.ContentObject;
import com.kakao.message.template.FeedTemplate;
import com.kakao.message.template.LinkObject;
import com.kakao.network.ErrorResult;
import com.kakao.network.callback.ResponseCallback;
import com.kakao.util.exception.KakaoException;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static android.widget.Toast.LENGTH_SHORT;
import static java.lang.System.exit;


public class MarketItemClick extends AppCompatActivity {
    TextView itemName;
    TextView itemPrice;
    TextView myPoint;
    ImageView itemImage;
    Button buybtn;
    ImageView heart;
    CheckBox checkBox;
    String id;      //파이어베이스 user
    int point;
    boolean isExist;
    boolean canBuy;
    boolean isLove;
    Dialog dialog;

    FirebaseAuth mAuth;
    FirebaseDatabase mDatabase;
    DatabaseReference mReference;
    FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.market_item_click);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance();
        mReference = mDatabase.getReference();

        // 사용자 id  받아오기
        String email = user.getEmail();
        int index = email.indexOf("@");
        String idt = email.substring(0, index);
        String web = email.substring(index+1);
        int webidx = web.indexOf(".");
        String website = web.substring(0, webidx);
        id = idt + "_"+website;

        Intent intent = getIntent();
        String itemN = intent.getStringExtra("name");
        int itemP = intent.getIntExtra("price", 0);
        String itemUri = intent.getStringExtra("imageURI");

        itemName = (TextView) findViewById(R.id.itemName);
        itemPrice = (TextView) findViewById(R.id.itemPrice);
        myPoint = (TextView) findViewById(R.id.myPoint);
        itemImage = (ImageView) findViewById(R.id.itemImage);
        buybtn = (Button) findViewById(R.id.buybtn);
        checkBox = (CheckBox) findViewById(R.id.checkBox);
        heart = (ImageView) findViewById(R.id.heart);
        canBuy = true;

        itemName.setText(itemN);
        buybtn.setEnabled(false);

        // 품절이면 soldout 사진표시
        isExist = false;
        mReference.child("MarketItems").child(itemN).child("count").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if((int)dataSnapshot.getValue(Integer.class) <= 0) {
                    isExist = false;
                    itemImage.setImageResource(R.drawable.soldout);
                    itemPrice.setText("품절");
                    buybtn.setEnabled(false);
                } else {
                    isExist = true;
                    Glide.with(MarketItemClick.this).load(itemUri)
                            .error(R.drawable.loading)
                            .into(itemImage);
                    itemPrice.setText(itemP + "P");
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        //포인트 잔액 - 파이어베이스 연동
        mReference.child("users").child(id).child("Totalpoint").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                point = (int) dataSnapshot.getValue(Integer.class);
                if(point < itemP) canBuy = false;
                myPoint.setText(point+"");

                //구매버튼 클릭
                buybtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(canBuy) {
                            //날짜 및 시간 형식 지정
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            String time = simpleDateFormat.format(System.currentTimeMillis());
                            mReference.child("MarketItems").child(itemN).child("count").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    int count = (int) dataSnapshot.getValue(Integer.class);
                                    count--;
                                    mReference.child("MarketItems").child(itemN).child("count").setValue(count);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                            // users - [id] - marketHistory - [시간] - [아이템] 형식으로 파베 저장
                            mReference.child("users").child(id).child("Totalpoint").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    point = (int) dataSnapshot.getValue(Integer.class);
                                    int p = point - itemP;
                                    Toast.makeText(getApplicationContext(), "결제완료! 잔액:" + p + "원", Toast.LENGTH_SHORT).show();
                                    mReference.child("users").child(id).child("marketHistory").child(time).setValue(new BuyItem(itemN, itemP, p, time, ""));
                                    mReference.child("users").child(id).child("Totalpoint").setValue(p);
                                    Intent intent2 = new Intent(getApplicationContext(), itemBarcode.class);
                                    intent2.putExtra("name", itemN);
                                    intent2.putExtra("price", itemP);
                                    intent2.putExtra("afterPoint", p);
                                    intent2.putExtra("buyTime", time);
                                    intent2.putExtra("imageURI",itemUri);
                                    startActivity(intent2);
                                }

                                @Override
                                public void onCancelled(DatabaseError databaseError) {

                                }
                            });
                        }
                        else
                            Toast.makeText(getApplicationContext(), "잔액이 부족합니다.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        // 체크박스 표시해야 구매버튼 활성화
        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkBox.isChecked() && isExist)
                    buybtn.setEnabled(true);
                else buybtn.setEnabled(false);
            }
        });

        // 찜하기 기능
        mReference.child("users").child(id).child("zzim").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for(DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    if(dataSnapshot.getKey().equals(itemN)) {
                        boolean isHeart = dataSnapshot.getValue(Boolean.class);
                        if(isHeart) heart.setImageResource(R.drawable.heart);
                        else heart.setImageResource(R.drawable.blankheart);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {

            }
        });
        heart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mReference.child("users").child(id).child("zzim").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        for(DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            if (snapshot.getKey().equals(itemN)) {
                                boolean isHeart = snapshot.getValue(Boolean.class);
                                if (isHeart) {
                                    mReference.child("users").child(id).child("zzim").child(itemN).setValue(false);
                                    heart.setImageResource(R.drawable.blankheart);
                                } else {
                                    mReference.child("users").child(id).child("zzim").child(itemN).setValue(true);
                                    heart.setImageResource(R.drawable.heart);
                                }
                            }
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });
            }
        });


    }

}




