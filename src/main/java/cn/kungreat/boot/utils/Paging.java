package cn.kungreat.boot.utils;

import lombok.Getter;

@Getter
public class Paging {
	private Integer totalRow;			//读取到数据-的总行数
	private Integer totalPage;			//按分页计算的-总页数
	private Integer pageSize = 10;			//每页显示的-行数
	private Integer currentPage=1;		//当前所在的页数
	private Integer lastPage;			//上一页
	private Integer nextPage;			//下一页
	private Integer topPage =1;		//第一页
	private Integer endPage;			//最后一页
									//下面是计算显示页面的结果
	public void setData(Integer totalRow,Integer pageSize,Integer currentPage) {
		this.totalRow = totalRow;
		this.pageSize = pageSize;
		if (totalRow <= pageSize) {
			this.totalPage=1;
			this.endPage =1;
			this.currentPage =1;
		}else {
			this.totalPage= totalRow % pageSize == 0? totalRow / pageSize:totalRow / pageSize + 1;
			this.endPage = this.totalPage;
			this.currentPage = currentPage > this.endPage?this.endPage:currentPage;
		}
		this.lastPage = (this.currentPage-1) > 0 ? this.currentPage-1 : this.currentPage;
		this.nextPage = this.currentPage < this.totalPage ? this.currentPage + 1 : this.currentPage;
	}
	
	public Integer getStart() {
		return (this.currentPage-1)*this.pageSize;
	}

	public void setTotalRow(Integer totalRow) {
		this.totalRow = totalRow;
	}

	public void setTotalPage(Integer totalPage) {
		this.totalPage = totalPage;
	}

	public void setPageSize(Integer pageSize) {
		if (pageSize !=null){
			this.pageSize = pageSize;
		}

	}

	public void setCurrentPage(Integer currentPage) {
		if (currentPage !=null){
			this.currentPage = currentPage;
		}
	}

	public void setLastPage(Integer lastPage) {
		this.lastPage = lastPage;
	}

	public void setNextPage(Integer nextPage) {
		this.nextPage = nextPage;
	}

	public void setTopPage(Integer topPage) {
		this.topPage = topPage;
	}

	public void setEndPage(Integer endPage) {
		this.endPage = endPage;
	}
}
