package org.aksw.bibuploader;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bibsonomy.bibtex.parser.PostBibTeXParser;
import org.bibsonomy.common.enums.GroupingEntity;
import org.bibsonomy.common.enums.PostUpdateOperation;
import org.bibsonomy.model.BibTex;
import org.bibsonomy.model.Post;
import org.bibsonomy.model.Resource;
import org.bibsonomy.model.User;
import org.bibsonomy.model.enums.Order;
import org.bibsonomy.model.logic.LogicInterface;
import org.bibsonomy.rest.client.RestLogicFactory;

public class BibUpdater {

	private final LogicInterface logic;

	private String username;

	private String fileLocation;

	private static Log log = LogFactory.getLog(BibUpdater.class);

	public BibUpdater(String username, String apikey, String apiurl, String fileLocation) {
		log.debug("Creating a new BibUpdater.");
		this.username = username;
		this.fileLocation = fileLocation;

		final RestLogicFactory rlf = new RestLogicFactory();
		logic = rlf.getLogicAccess(username, apikey);

	}

	public BibUpdater(String fileLocation) {

		log.debug("Creating a new BibChecker.");
		this.fileLocation = fileLocation;
		logic = null;
	}

	public static void main(String[] args) {

		if (args.length == 1) {

			try {

				BibUpdater checker = new BibUpdater(args[0]);
				checker.loadEntriesFromFile();

			} catch (Exception e) {

				throw new RuntimeException("Error occured:" + e.getMessage(), e);

			}

		} else if (args.length != 4) {

			log.error("call with parameters: username apikey apiurl file");

		} else {

			try {

				BibUpdater update = new BibUpdater(args[0], args[1], args[2], args[3]);
				// update.diffUpdate();
				// update.flushNpush();
				update.updateAccount();

			} catch (Exception e) {

				log.error("Error occured:", e);
			}

		}

	}

	public void deleteEntry(Post<BibTex> post) throws Exception {
		String intraHash = post.getResource().getIntraHash();
		logic.deletePosts(username, Collections.<String>singletonList(intraHash));
	}

	private void flushNpush() throws Exception {
		// load entries
		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

		// get all previously posted entries
		List<Post<BibTex>> accountEntries = loadEntriesFromAccount();

		// and delete them
		for (Post<BibTex> post : accountEntries) {
			deleteEntry(post);
			log.info(post.getResource().getTitle() + " deleted");
		}

		// upload them
		for (Post<BibTex> post : fileEntries) {
			uploadEntry(post);
			log.info(post.getResource().getTitle() + " uploaded");
		}

	}

	public List<Post<BibTex>> loadEntriesFromFile() throws Exception {

		String bibtexString = IOUtils.toString(new FileInputStream(this.fileLocation), "UTF-8");

		PostBibTeXParser parser = new PostBibTeXParser();

		List<Post<BibTex>> posts = parser.parseBibTeXPosts(bibtexString);

		for (Post<BibTex> post : posts) {
			post.getResource().recalculateHashes();
		}

		return posts;

	}

	public List<Post<BibTex>> loadEntriesFromAccount() throws Exception {

		List<Post<BibTex>> publications = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null,
				null, null, Order.ADDED, null, null, 0, 1000);
		return publications;

	}

	public List<String> uploadEntry(Post<BibTex> entry) {

		entry.setUser(new User(this.username));

		if (entry.getTags() == null || entry.getTags().isEmpty()) {
			entry.addTag("nokeyword");
			log.warn("Please add keywords for entry: " + entry.getResource().getTitle());
		}

		return logic.createPosts(Collections.<Post<? extends Resource>>singletonList(entry));

	}

	public void diffUpdate() throws Exception {

		// get all previously posted entries

		List<Post<BibTex>> accountEntries = loadEntriesFromAccount();

		// store the hashes conveniently
		List<String> accountHashes = new ArrayList<String>();

		for (Post<BibTex> post : accountEntries) {
			accountHashes.add(post.getResource().getIntraHash());
		}

		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

		// if not already stored, upload them
		for (Post<BibTex> post : fileEntries) {
			if (!accountHashes.contains(post.getResource().getIntraHash())) {
				uploadEntry(post);
				log.info(post.getResource().getTitle() + " uploaded");
			} else {
				log.info(post.getResource().getTitle() + " was already there");
			}
		}

	}

	/**
	 * Updates the account entries (B) based on the file entries (F)
	 * 
	 * @throws Exception
	 */
	private void updateAccount() throws Exception {
		// load entries
		List<Post<BibTex>> fileEntries = loadEntriesFromFile();

		// get all previously posted entries
		List<Post<BibTex>> accountEntries = loadAllEntriesFromAccount();

		Summary summary = new Summary();

		// remove duplicates from file and identifies posts with missing keywords
		Set<String> seen = new HashSet<String>();
		Iterator<Post<BibTex>> iter = fileEntries.iterator();
		while (iter.hasNext()) {
			Post<BibTex> entry = iter.next();
			if (entry.getTags() == null || entry.getTags().isEmpty())
				summary.addNoTagEntry(entry.getResource().getTitle());

			if (!seen.add(entry.getResource().getIntraHash())) {
				summary.addDuplicate(entry.getResource().getTitle());
				iter.remove();
			}
		}

		// present in B and in F, updates based on file entry if different
		List<Post<BibTex>> intersection = getPaperIntersection(fileEntries, accountEntries);
		for (Post<BibTex> post : intersection) {
			Post<BibTex> matchingPost = accountEntries.stream()
					.filter(a -> a.getResource().getIntraHash().equals(post.getResource().getIntraHash())).findFirst()
					.orElse(null);
			if (matchingPost != null) {
				try {
					if (!isSame(matchingPost, post)) {
						updateEntry(post);
						summary.addUpdate();
					}
				} catch (Exception e) {
					e.printStackTrace();
					log.warn("Post could not be updated " + post.getResource().getBibtexKey());
				}
			}
		}

		// present in B, not in F, is removed
		List<Post<BibTex>> removeEntries = getExclusive(accountEntries, intersection);
		deleteEntries(removeEntries);
		summary.setRemoved(removeEntries.size());

		// present in F, not in B, is added
		List<Post<BibTex>> addEntries = getExclusive(fileEntries, intersection);
		for (Post<BibTex> post : addEntries) {
			String postTitle = post.getResource().getTitle();
			if(uploadEntry(post).isEmpty()) {
				log.warn(postTitle+" failed to upload");
				summary.addFailAdd(postTitle);
			} else {
				summary.addSucAdd();
				log.info(postTitle + " uploaded");
			}			
		}
		log.info(summary.toString());
	}

	public List<Post<BibTex>> loadAllEntriesFromAccount() throws Exception {
		int cur = 0;
		final int max_entries = 1000;

		// it can only retrieve 1000 posts at a time
		List<Post<BibTex>> publications = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null,
				null, null, Order.ADDED, null, null, cur, cur + max_entries);
		while (publications.size() == cur + max_entries) {
			cur += max_entries;
			List<Post<BibTex>> posts = logic.getPosts(BibTex.class, GroupingEntity.USER, username, null, null, null,
					null, null, Order.ADDED, null, null, cur, cur + max_entries);
			if (posts.isEmpty())
				break;
			publications.addAll(posts);

		}
		return publications;

	}

	private void updateEntry(Post<BibTex> entry) {
		entry.setUser(logic.getAuthenticatedUser());
		List<Post<? extends Resource>> post = Collections.<Post<? extends Resource>>singletonList(entry);
		logic.updatePosts(post, PostUpdateOperation.UPDATE_ALL);

	}

	private void deleteEntries(List<Post<BibTex>> posts) {
		logic.deletePosts(username,
				posts.stream().map(p -> p.getResource().getIntraHash()).collect(Collectors.toList()));
	}

	private List<Post<BibTex>> getPaperIntersection(List<Post<BibTex>> lista, List<Post<BibTex>> listb) {
		return lista.stream()
				.filter(f -> listb.stream()
						.anyMatch(b -> b.getResource().getIntraHash().equals(f.getResource().getIntraHash())))
				.collect(Collectors.toList());
	}

	private List<Post<BibTex>> getExclusive(List<Post<BibTex>> list, List<Post<BibTex>> intersection) {
		return list.stream()
				.filter(b -> intersection.stream()
						.noneMatch(i -> i.getResource().getIntraHash().equals(b.getResource().getIntraHash())))
				.collect(Collectors.toList());
	}

	/**
	 * Returns true if both the account and the file entry are the same post.
	 * Bibsonomy adds some fields when uploading an entry, therefore these fields
	 * are ignored in this comparison.
	 * 
	 * @param accountEntry the post online
	 * @param filePost     the post in the file
	 * @return
	 * @throws NoSuchFieldException
	 * @throws SecurityException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	public boolean isSame(Post<BibTex> accountEntry, Post<BibTex> filePost)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

		if (filePost.getTags() == null || filePost.getTags().isEmpty())
			filePost.addTag("nokeyword");

		// declare the fields that are allowed to be different and still correspond to a
		// pair
		Class<Post> clazz = Post.class;
		List<Field> allowedFields = Arrays.asList(clazz.getDeclaredField("user"), clazz.getDeclaredField("groups"),
				clazz.getDeclaredField("changeDate"), clazz.getDeclaredField("date"));

		// get all fields
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			field.setAccessible(true);
			// dismiss fields that are allowed to differ
			if (allowedFields.contains(field)) {
				continue;
			}

			// if it's the resource field, check the fields inside also
			if (field.getType().isAssignableFrom(BibTex.class)) {
				Field[] bibFields = BibTex.class.getDeclaredFields();
				for (Field bibField : bibFields) {
					bibField.setAccessible(true);
					Object accBib = bibField.get(accountEntry.getResource());
					Object fileBib = bibField.get(filePost.getResource());

					// entries might have empty lists or maps instead of null references
					accBib = checkNullOrEmpty(accBib);
					fileBib = checkNullOrEmpty(fileBib);

					// check if anything differs inside
					if ((accBib == null && accBib != fileBib) || (accBib != null && !accBib.equals(fileBib))) {
						return false;
					}
				}
				continue;
			}

			// check if anything else differs
			Object accountAtt = field.get(accountEntry);
			Object fileAtt = field.get(filePost);

			accountAtt = checkNullOrEmpty(accountAtt);
			fileAtt = checkNullOrEmpty(fileAtt);

			if ((accountAtt == null && accountAtt != fileAtt) || (accountAtt != null && !accountAtt.equals(fileAtt))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Sets object to null if the object is an instance of {@Link List}, {@link Map}
	 * or {@link Set} and is empty
	 * 
	 * @param obj
	 */
	private Object checkNullOrEmpty(Object obj) {
		if (obj != null && obj instanceof List && ((List<?>) obj).isEmpty()) {
			return null;
		} else if (obj != null && obj instanceof Set && ((Set<?>) obj).isEmpty()) {
			return null;
		} else if (obj != null && obj instanceof Map && ((Map<?, ?>) obj).isEmpty()) {
			return null;
		} else {
			return obj;
		}
	}

}
