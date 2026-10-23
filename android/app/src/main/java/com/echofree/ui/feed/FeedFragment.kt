package com.echofree.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.echofree.R
import com.echofree.data.models.Post
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class FeedFragment : Fragment() {

    private val viewModel: FeedViewModel by viewModels()
    private lateinit var adapter: PostAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var chipFeed: Chip
    private lateinit var chipExplore: Chip

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_feed, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        recyclerView  = view.findViewById(R.id.recycler_feed)
        emptyView     = view.findViewById(R.id.tv_empty)
        chipFeed      = view.findViewById(R.id.chip_feed)
        chipExplore   = view.findViewById(R.id.chip_explore)

        adapter = PostAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        chipFeed.setOnClickListener { viewModel.setExploreMode(false) }
        chipExplore.setOnClickListener { viewModel.setExploreMode(true) }

        viewModel.isExploreMode.observe(viewLifecycleOwner) { explore ->
            chipExplore.isChecked = explore
            chipFeed.isChecked = !explore
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            swipeRefresh.isRefreshing = false
            when (state) {
                is FeedUiState.Loading -> swipeRefresh.isRefreshing = true
                is FeedUiState.Success -> {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(state.posts)
                }
                is FeedUiState.Empty -> {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = "No posts yet — be the first to share something."
                }
                is FeedUiState.Error -> {
                    Snackbar.make(view, state.message, Snackbar.LENGTH_LONG)
                        .setAction("Retry") { viewModel.refresh() }
                        .show()
                }
            }
        }

        view.findViewById<FloatingActionButton>(R.id.fab_create).setOnClickListener {
            findNavController().navigate(R.id.action_feed_to_create)
        }
    }
}

class PostAdapter : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val items = mutableListOf<Post>()

    fun submitList(posts: List<Post>) {
        items.clear()
        items.addAll(posts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(v)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvAvatar:  TextView  = view.findViewById(R.id.tv_avatar)
        private val tvAuthor:  TextView  = view.findViewById(R.id.tv_author)
        private val tvContent: TextView  = view.findViewById(R.id.tv_content)
        private val tvTime:    TextView  = view.findViewById(R.id.tv_time)
        private val chipGroup: ChipGroup = view.findViewById(R.id.chip_group_tags)

        fun bind(post: Post) {
            tvAvatar.text = post.authorName.take(1).uppercase()
            tvAuthor.text  = post.authorName
            tvContent.text = post.content
            tvTime.text    = formatRelativeTime(post.createdAt)

            chipGroup.removeAllViews()
            post.tags.take(4).forEach { tag ->
                val chip = Chip(itemView.context).apply {
                    text = "#$tag"
                    isClickable = false
                    setPadding(0,0,0,0)
                }
                chipGroup.addView(chip)
            }
        }

        private fun formatRelativeTime(epochMs: Long): String {
            val diff = System.currentTimeMillis() - epochMs
            return when {
                diff < 60_000        -> "just now"
                diff < 3_600_000     -> "${diff / 60_000}m ago"
                diff < 86_400_000    -> "${diff / 3_600_000}h ago"
                else                 -> "${diff / 86_400_000}d ago"
            }
        }
    }
}
