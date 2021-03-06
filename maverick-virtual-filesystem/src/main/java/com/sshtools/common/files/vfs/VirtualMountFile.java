/**
 * (c) 2002-2019 JADAPTIVE Limited. All Rights Reserved.
 *
 * This file is part of the Maverick Synergy Java SSH API.
 *
 * Maverick Synergy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Maverick Synergy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Maverick Synergy.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.sshtools.common.files.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.sshtools.common.files.AbstractFile;
import com.sshtools.common.files.AbstractFileFactory;
import com.sshtools.common.files.AbstractFileRandomAccess;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.sftp.SftpFileAttributes;
import com.sshtools.common.util.FileUtils;

public class VirtualMountFile implements VirtualFile {

	private VirtualMount mount;
	private VirtualFileFactory fileFactory;
	private String name;
	private String path;
	private AbstractFile file;
	
	public VirtualMountFile(String path, VirtualMount mount, VirtualFileFactory fileFactory) throws PermissionDeniedException, IOException {
		this.mount = mount;
		this.fileFactory = fileFactory;
		int idx = path.lastIndexOf('/');
		if(idx > -1) {
			name = path.substring(idx+1);
		} else {
			name = path;
		}
		this.path = path;
		file = mount.getActualFileFactory().getFile(mount.getResolvePath(path));
	}
	
	public boolean exists() throws IOException, PermissionDeniedException {
		return file.exists();
	}

	public boolean createFolder() throws PermissionDeniedException, IOException {
		return file.createFolder();
	}

	public long lastModified() throws IOException, PermissionDeniedException {
		return file.lastModified();
	}

	public String getName() {
		return name;
	}

	public long length() throws IOException, PermissionDeniedException {
		return file.length();
	}

	public SftpFileAttributes getAttributes() throws FileNotFoundException,
			IOException, PermissionDeniedException {
		return file.getAttributes();
	}

	public boolean isHidden() throws IOException, PermissionDeniedException {
		return file.isHidden();
	}

	public boolean isDirectory() throws IOException, PermissionDeniedException {
		return file.isDirectory();
	}

	public List<AbstractFile> getChildren() throws IOException,
			PermissionDeniedException {

		List<AbstractFile> files = new ArrayList<AbstractFile>();
		
		String currentPath = FileUtils.checkEndsWithSlash(path);

		VirtualMountManager mgr = fileFactory.getMountManager();
		
		if(mount.isFilesystemRoot()) {
			for(VirtualMount m : mgr.getMounts()) {
				if(m.getMount().startsWith(currentPath)) {
					if(m.getMount().equals(currentPath)) {
						// We need to list the contents of the actual folder
						VirtualMount actualMount = mgr.getMount(currentPath);
						AbstractFile parent = actualMount.getActualFileFactory().getFile(actualMount.getRoot());
						files.addAll(parent.getChildren());
					} else {
						String child = m.getMount().substring(currentPath.length());
						if(child.indexOf('/') > -1) {
							child = child.substring(0,child.indexOf('/'));
						}
						files.add(new VirtualMountFile(currentPath + child, m, fileFactory));
					}
				}
			}
		} else {
			// Just return the next path element from the mount path.
			String child = mount.getMount().substring(path.length());
			if(child.indexOf('/') > -1) {
				child = child.substring(0,child.indexOf('/'));
			}
			files.add(new VirtualMountFile(path + child, mount, fileFactory));
		}
		
		return files;

	}

	public boolean isFile() throws IOException, PermissionDeniedException {
		return file.isFile();
	}

	public String getAbsolutePath() throws IOException,
			PermissionDeniedException {
		return path;
	}

	public InputStream getInputStream() throws IOException, PermissionDeniedException {
		return file.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException, PermissionDeniedException {
		return file.getOutputStream();
	}

	public boolean isReadable() throws IOException, PermissionDeniedException {
		return file.isReadable();
	}

	public void copyFrom(AbstractFile src) throws IOException,
			PermissionDeniedException {
		file.copyFrom(src);
	}

	public void moveTo(AbstractFile target) throws IOException,
			PermissionDeniedException {
		file.moveTo(target);
	}

	public boolean delete(boolean recursive) throws IOException,
			PermissionDeniedException {
		return file.delete(recursive);
	}

	public void refresh() {
		file.refresh();
	}

	public boolean isWritable() throws IOException, PermissionDeniedException {
		return file.isWritable();
	}

	public boolean createNewFile() throws PermissionDeniedException,
			IOException {
		return file.createNewFile();
	}

	public void truncate() throws PermissionDeniedException, IOException {
		file.truncate();
	}

	public void setAttributes(SftpFileAttributes attrs) throws IOException {
		file.setAttributes(attrs);
	}

	public String getCanonicalPath() throws IOException,
			PermissionDeniedException {
		return path;
	}

	public boolean supportsRandomAccess() {
		return file.supportsRandomAccess();
	}

	public AbstractFileRandomAccess openFile(boolean writeAccess)
			throws IOException, PermissionDeniedException {
		return file.openFile(writeAccess);
	}

	public OutputStream getOutputStream(boolean append) throws IOException, PermissionDeniedException {
		return file.getOutputStream(append);
	}

	public AbstractFile resolveFile(String child) throws IOException, PermissionDeniedException {
		if(child.startsWith("/")) {
			return fileFactory.getFile(child);
		} else {
			return fileFactory.getFile(path + (path.equals("/") || path.endsWith("/") ? "" : "/") + child);
		}
	}

	public AbstractFileFactory<VirtualFile> getFileFactory() {
		return mount.getVirtualFileFactory();
	}

}
