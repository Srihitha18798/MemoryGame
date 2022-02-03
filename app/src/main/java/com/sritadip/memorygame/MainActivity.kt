package com.sritadip.memorygame

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import com.sritadip.memorygame.models.BoardSize
import com.sritadip.memorygame.models.MemoryGame
import com.sritadip.memorygame.models.UserImageList
import com.sritadip.memorygame.utils.EXTRA_BOARD_SIZE
import com.sritadip.memorygame.utils.EXTRA_GAME_NAME
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var boardSize: BoardSize = BoardSize.EASY
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private var customGameImages:List<String>?=null
    private val db=Firebase.firestore
    private var gameName:String?=null

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE=248
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       
        setupBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.mi_refresh -> {
                if((memoryGame.getNumMoves()>0) && (!memoryGame.haveWonGame())){
                       showAlertDialog("Quit your current game",null,View.OnClickListener {
                           setupBoard()

                       })
                }
                else{
                    setupBoard()

                }
                return true
            }
            R.id.mi_new_size->{
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom->{
                showCreateDialog()
                return true
            }
            R.id.mi_download->{
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode== CREATE_REQUEST_CODE && resultCode==Activity.RESULT_OK){
            val customGameName=data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName==null){
                Log.e(TAG,"Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val boardDownloadView=LayoutInflater.from(this).inflate(R.layout.dialog_download_board,null)
        showAlertDialog("Fetch memory game",boardDownloadView,View.OnClickListener {
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })

    }
    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener {document->
            val userImageList=document.toObject(UserImageList::class.java)
            if(userImageList?.images==null){
                Log.e(TAG,"Invalid custom game data from Firestore")
                Snackbar.make(clRoot,"Sorry, we couldn't find any such game, '$gameName'",Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards=userImageList.images.size*2
            boardSize= BoardSize.getByValue(numCards)
            customGameImages=userImageList.images
            for (imageUrl in userImageList.images){
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot,"Your now playing '$customGameName'!",Snackbar.LENGTH_LONG).show()
            gameName=customGameName
            setupBoard()

        }.addOnFailureListener{exception->
            Log.e(TAG,"Exception when retrieving game",exception)
        }


    }

    private fun showCreateDialog() {
        val boardSizeView=LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize=boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create your own memory board",boardSizeView,View.OnClickListener {
            val desiredBoardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy->BoardSize.EASY
                R.id.rbMedium->BoardSize.MEDIUM

                else ->BoardSize.HARD
            }
            val intent = Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE,desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)

        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView=LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize=boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose new size",boardSizeView,View.OnClickListener {
                boardSize=when(radioGroupSize.checkedRadioButtonId){
                    R.id.rbEasy->BoardSize.EASY
                    R.id.rbMedium->BoardSize.MEDIUM

                    else ->BoardSize.HARD
                }
            gameName=null
            customGameImages=null
            setupBoard()
        })
    }

    private fun showAlertDialog(title:String,view:View?,positiveButtonClickListener: View.OnClickListener) {
        AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Cancel",null)
                .setPositiveButton("OK"){ _, _ ->
                        positiveButtonClickListener.onClick(null)

                }.show()

    }

    private fun setupBoard() {
        supportActionBar?.title=gameName ?: getString(R.string.app_name)
        when(boardSize){
            BoardSize.EASY -> {
                tvNumMoves.text="Easy: 4 x 2"
                tvNumPairs.text="Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text="Easy: 6 x 3"
                tvNumPairs.text="Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text="Easy: 6 x 4"
                tvNumPairs.text="Pairs: 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this,R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize,customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object : MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                //Log.i(TAG,"Card clicked $position")
                updateGameWithFlip(position)
            }

        })
        rvBoard.adapter = adapter

        rvBoard.setHasFixedSize(true)

        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun updateGameWithFlip(position: Int) {

        if (memoryGame.haveWonGame()) {
            Snackbar.make(clRoot, getString(R.string.AlreadyWon), Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(clRoot, getString(R.string.InvalidMove), Snackbar.LENGTH_SHORT).show()
            return
        }
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
            val color=ArgbEvaluator().evaluate(
                    memoryGame.numPairsFound.toFloat()/boardSize.getNumPairs(),
                    ContextCompat.getColor(this,R.color.color_progress_none),
                    ContextCompat.getColor(this,R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, getString(R.string.GameWon), Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW,Color.GREEN,Color.MAGENTA)).oneShot()

            }

        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()

    }
}