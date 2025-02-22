package com.fsck.k9.ui.messageview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.MessageListItem
import com.fsck.k9.ui.messagelist.MessageListViewModel
import com.fsck.k9.ui.withArguments

/**
 * A fragment that uses [ViewPager2] to allow the user to swipe between messages.
 *
 * Individual messages are displayed using a [MessageViewFragment].
 */
class MessageViewContainerFragment : Fragment() {
    var isActive: Boolean = false
        set(value) {
            field = value
            setMenuVisibility(value)
        }

    lateinit var messageReference: MessageReference
        private set

    private var activeMessageReference: MessageReference? = null

    var lastDirection: Direction? = null
        private set

    private lateinit var fragmentListener: MessageViewContainerListener
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MessageViewContainerAdapter

    private var currentPosition: Int? = null

    private val messageViewFragment: MessageViewFragment
        get() {
            check(isResumed)
            return findMessageViewFragment()
        }

    private fun findMessageViewFragment(): MessageViewFragment {
        val itemId = adapter.getItemId(messageReference)

        // ViewPager2/FragmentStateAdapter don't provide an easy way to get hold of the Fragment for the active
        // page. So we're using an implementation detail (the fragment tag) to find the fragment.
        return childFragmentManager.findFragmentByTag("f$itemId") as MessageViewFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        if (savedInstanceState == null) {
            messageReference = MessageReference.parse(arguments?.getString(ARG_REFERENCE))
                ?: error("Missing argument $ARG_REFERENCE")
        } else {
            messageReference = MessageReference.parse(savedInstanceState.getString(STATE_MESSAGE_REFERENCE))
                ?: error("Missing state $STATE_MESSAGE_REFERENCE")

            lastDirection = savedInstanceState.getSerializable(STATE_LAST_DIRECTION) as Direction?
        }

        adapter = MessageViewContainerAdapter(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        fragmentListener = try {
            context as MessageViewContainerListener
        } catch (e: ClassCastException) {
            throw ClassCastException("This fragment must be attached to a MessageViewContainerListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.message_view_container, container, false)

        val resources = inflater.context.resources
        val pageMargin = resources.getDimension(R.dimen.message_view_pager_page_margin).toInt()

        viewPager = view.findViewById(R.id.message_viewpager)
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        viewPager.setPageTransformer(MarginPageTransformer(pageMargin))
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            // The message list is updated each time the active message is changed. To avoid message list updates
            // during the animation, we only set the active message after the animation has finished.
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    setActiveMessage(viewPager.currentItem)
                }
            }

            override fun onPageSelected(position: Int) {
                if (viewPager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                    setActiveMessage(position)
                }
            }
        })

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MESSAGE_REFERENCE, messageReference.toIdentityString())
        outState.putSerializable(STATE_LAST_DIRECTION, lastDirection)
    }

    fun setViewModel(viewModel: MessageListViewModel) {
        viewModel.getMessageListLiveData().observe(this) { messageListInfo ->
            updateMessageList(messageListInfo.messageListItems)
        }
    }

    private fun updateMessageList(messageListItems: List<MessageListItem>) {
        if (messageListItems.isEmpty() || messageListItems.none { it.messageReference == messageReference }) {
            fragmentListener.closeMessageView()
            return
        }

        adapter.messageList = messageListItems

        // We only set the adapter on ViewPager2 after the message list has been loaded. This way ViewPager2 can
        // restore its saved state after a configuration change.
        if (viewPager.adapter == null) {
            viewPager.adapter = adapter
        }

        val position = adapter.getPosition(messageReference)
        viewPager.setCurrentItem(position, false)
    }

    private fun setActiveMessage(position: Int) {
        // If the position of current message changes (e.g. because messages were added or removed from the list), we
        // keep track of the new position but otherwise ignore the event.
        val newMessageReference = adapter.getMessageReference(position)
        if (newMessageReference == activeMessageReference) {
            currentPosition = position
            return
        }

        rememberNavigationDirection(position)

        messageReference = adapter.getMessageReference(position)

        activeMessageReference = messageReference
        fragmentListener.setActiveMessage(messageReference)
    }

    private fun rememberNavigationDirection(newPosition: Int) {
        currentPosition?.let { currentPosition ->
            lastDirection = if (newPosition < currentPosition) Direction.PREVIOUS else Direction.NEXT
        }
        currentPosition = newPosition
    }

    fun showPreviousMessage(): Boolean {
        val newPosition = viewPager.currentItem - 1
        return if (newPosition >= 0) {
            setActiveMessage(newPosition)

            val smoothScroll = true
            viewPager.setCurrentItem(newPosition, smoothScroll)
            true
        } else {
            false
        }
    }

    fun showNextMessage(): Boolean {
        val newPosition = viewPager.currentItem + 1
        return if (newPosition < adapter.itemCount) {
            setActiveMessage(newPosition)

            val smoothScroll = true
            viewPager.setCurrentItem(newPosition, smoothScroll)
            true
        } else {
            false
        }
    }

    fun onToggleFlagged() {
        messageViewFragment.onToggleFlagged()
    }

    fun onMove() {
        messageViewFragment.onMove()
    }

    fun onArchive() {
        messageViewFragment.onArchive()
    }

    fun onCopy() {
        messageViewFragment.onCopy()
    }

    fun onToggleRead() {
        messageViewFragment.onToggleRead()
    }

    fun onForward() {
        messageViewFragment.onForward()
    }

    fun onReplyAll() {
        messageViewFragment.onReplyAll()
    }

    fun onReply() {
        messageViewFragment.onReply()
    }

    fun onDelete() {
        messageViewFragment.onDelete()
    }

    fun onPendingIntentResult(requestCode: Int, resultCode: Int, data: Intent?) {
        findMessageViewFragment().onPendingIntentResult(requestCode, resultCode, data)
    }

    private class MessageViewContainerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        var messageList: List<MessageListItem> = emptyList()
            set(value) {
                val diffResult = DiffUtil.calculateDiff(
                    MessageListDiffCallback(oldMessageList = messageList, newMessageList = value)
                )

                field = value

                diffResult.dispatchUpdatesTo(this)
            }

        override fun getItemCount(): Int {
            return messageList.size
        }

        override fun getItemId(position: Int): Long {
            return messageList[position].uniqueId
        }

        override fun containsItem(itemId: Long): Boolean {
            return messageList.any { it.uniqueId == itemId }
        }

        override fun createFragment(position: Int): Fragment {
            check(position in messageList.indices)

            val messageReference = messageList[position].messageReference
            return MessageViewFragment.newInstance(messageReference)
        }

        fun getMessageReference(position: Int): MessageReference {
            check(position in messageList.indices)

            return messageList[position].messageReference
        }

        fun getPosition(messageReference: MessageReference): Int {
            return messageList.indexOfFirst { it.messageReference == messageReference }
        }

        fun getItemId(messageReference: MessageReference): Long {
            return messageList.first { it.messageReference == messageReference }.uniqueId
        }
    }

    private class MessageListDiffCallback(
        private val oldMessageList: List<MessageListItem>,
        private val newMessageList: List<MessageListItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldMessageList.size

        override fun getNewListSize(): Int = newMessageList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldMessageList[oldItemPosition].uniqueId == newMessageList[newItemPosition].uniqueId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            // Let MessageViewFragment deal with content changes
            return areItemsTheSame(oldItemPosition, newItemPosition)
        }
    }

    interface MessageViewContainerListener {
        fun closeMessageView()
        fun setActiveMessage(messageReference: MessageReference)
    }

    companion object {
        private const val ARG_REFERENCE = "reference"

        private const val STATE_MESSAGE_REFERENCE = "messageReference"
        private const val STATE_LAST_DIRECTION = "lastDirection"

        fun newInstance(reference: MessageReference): MessageViewContainerFragment {
            return MessageViewContainerFragment().withArguments(
                ARG_REFERENCE to reference.toIdentityString()
            )
        }
    }
}
